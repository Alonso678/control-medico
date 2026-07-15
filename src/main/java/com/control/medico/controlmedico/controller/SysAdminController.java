package com.control.medico.controlmedico.controller;

import com.control.medico.controlmedico.model.Familia;
import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.TipoMembresia;
import com.control.medico.controlmedico.service.AdminGlobalService;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import com.control.medico.controlmedico.repository.FamiliaRepository; // <-- 1. Importar el repositorio de familias

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // <-- Para mensajes de éxito/error

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/sys-admin")
@PreAuthorize("hasRole('SYS_ADMIN')") // Protección estricta a nivel de clase
public class SysAdminController {

    private final AdminGlobalService adminGlobalService;
    private final FamiliarRepository familiarRepository;
    private final FamiliaRepository familiaRepository; // <-- 2. Declarar el repositorio de familias

    // 3. Inyectar en el constructor original
    public SysAdminController(AdminGlobalService adminGlobalService,
            FamiliarRepository familiarRepository,
            FamiliaRepository familiaRepository) {
        this.adminGlobalService = adminGlobalService;
        this.familiarRepository = familiarRepository;
        this.familiaRepository = familiaRepository;
    }

    // Dashboard Central de Superusuario (MODIFICADO para soportar creación)
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<Familia> familias = adminGlobalService.obtenerFamiliasPaginadas(page);
        model.addAttribute("familias", familias);
        model.addAttribute("currentPage", page);

        // <-- NUEVO: Objeto vacío necesario para mapear el formulario del modal de
        // creación
        model.addAttribute("nuevaFamilia", new Familia());

        return "dashboard"; // Tu HTML actual de la suite SYS_ADMIN
    }

    // ==========================================
    // NUEVOS ENDPOINTS PARA EL CRUD DE FAMILIAS
    // ==========================================

    // [CREATE / UPDATE] Guardar o actualizar una familia
    @PostMapping("/familia/guardar")
    public String guardarFamilia(@ModelAttribute("nuevaFamilia") Familia familia,
            RedirectAttributes redirectAttributes) {
        try {
            if (familia.getId() == null) {
                // 1. Establecer fecha de alta
                familia.setFechaCreacion(LocalDateTime.now());

                // 2. Activar por defecto
                familia.setActivo(true);

                // 3. AUTOGENERAR CÓDIGO DE ACCESO (Si viene nulo o vacío)
                if (familia.getCodigoAcceso() == null || familia.getCodigoAcceso().trim().isEmpty()) {
                    // Convertimos a mayúsculas, quitamos la palabra "FAMILIA" y eliminamos todos
                    // los espacios
                    String nombreDepurado = familia.getNombre()
                            .toUpperCase()
                            .replace("FAMILIA", "") // Omitir la palabra 'FAMILIA'
                            .replaceAll("\\s+", ""); // Quita todos los espacios en blanco restantes

                    // Limita el nombre depurado a 8 caracteres + año actual (Ej: "ORTIZSAL2026")
                    String prefijo = nombreDepurado.substring(0, Math.min(nombreDepurado.length(), 8));
                    int anioActual = java.time.Year.now().getValue();

                    familia.setCodigoAcceso(prefijo + anioActual);
                } else {
                    // Si el formulario sí lo envió, lo aseguramos en mayúsculas y sin espacios
                    familia.setCodigoAcceso(familia.getCodigoAcceso().trim().toUpperCase());
                }

                familiaRepository.save(familia);
                redirectAttributes.addFlashAttribute("mensajeExito", "Familia creada correctamente.");

            } else {
                // --- BLOQUE DE EDICIÓN ---
                Familia familiaExistente = familiaRepository.findById(familia.getId())
                        .orElseThrow(() -> new RuntimeException("Familia no encontrada"));

                familiaExistente.setNombre(familia.getNombre());

                // Si el código de acceso se puede editar en tu vista:
                if (familia.getCodigoAcceso() != null && !familia.getCodigoAcceso().trim().isEmpty()) {
                    familiaExistente.setCodigoAcceso(familia.getCodigoAcceso().toUpperCase());
                }

                if (familia.getActivo() != null) {
                    familiaExistente.setActivo(familia.getActivo());
                }

                familiaRepository.save(familiaExistente);
                redirectAttributes.addFlashAttribute("mensajeExito", "Familia actualizada correctamente.");
            }
        } catch (Exception e) {
            // Imprime el error real en la terminal para que puedas depurar si ocurre algo
            // más
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("mensajeError",
                    "Error al procesar la operación de la familia: " + e.getMessage());
        }
        return "redirect:/sys-admin/dashboard";
    }

    // [UPDATE - API Helper] Obtener datos de una familia para precargar el modal de
    // edición mediante JavaScript
    @GetMapping("/familia/datos/{id}")
    @ResponseBody
    public Familia obtenerDatosFamilia(@PathVariable("id") Long id) {
        return familiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Familia no encontrada"));
    }

    // [DELETE] Eliminar una familia
    @PostMapping("/familia/eliminar/{id}")
    public String eliminarFamilia(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            familiaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensajeExito", "Familia eliminada con éxito del sistema.");
        } catch (Exception e) {
            // Captura de error de integridad referencial si la familia tiene miembros
            // activos
            redirectAttributes.addFlashAttribute("mensajeError",
                    "No se puede eliminar la familia porque tiene usuarios o registros asociados.");
        }
        return "redirect:/sys-admin/dashboard";
    }

    // ==========================================
    // MÉTODOS ORIGINALES (SIN ALTERACIONES)
    // ==========================================

    // Ver y administrar los familiares de una familia elegida
    @GetMapping("/familia/{id}/miembros")
    public String gestionarMiembros(@PathVariable("id") Long familiaId, Model model) {
        List<Familiar> miembros = adminGlobalService.obtenerMiembrosPorFamilia(familiaId);
        model.addAttribute("miembros", miembros);
        model.addAttribute("familiaId", familiaId);
        model.addAttribute("tiposMembresia", TipoMembresia.values());
        return "miembros-detalle";
    }

    // Cambiar el tipo de membresía de un familiar desde la suite de superusuario
    @PostMapping("/miembro/actualizar-membresia")
    public String actualizarMembresia(@RequestParam("miembroId") Long miembroId,
            @RequestParam("tipoMembresia") TipoMembresia tipoMembresia,
            @RequestParam("familiaId") Long familiaId) {
        Familiar familiar = familiarRepository.findById(miembroId)
                .orElseThrow(() -> new RuntimeException("Miembro no encontrado"));
        familiar.setTipoMembresia(tipoMembresia);
        familiarRepository.save(familiar);
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros";
    }

    // Reasignar el administrador maestro de una familia específica
    @PostMapping("/familia/reasignar-admin")
    public String reasignarAdmin(@RequestParam("familiaId") Long familiaId,
            @RequestParam("nuevoAdminId") Long nuevoAdminId) {
        adminGlobalService.cambiarAdministradorDeFamilia(familiaId, nuevoAdminId);
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros";
    }
}