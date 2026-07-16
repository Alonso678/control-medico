package com.control.medico.controlmedico.controller;

import com.control.medico.controlmedico.model.Familia;
import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.TipoMembresia;
import com.control.medico.controlmedico.service.AdminGlobalService;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import com.control.medico.controlmedico.repository.FamiliaRepository;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/sys-admin")
@PreAuthorize("hasRole('SYS_ADMIN')") 
public class SysAdminController {

    private final AdminGlobalService adminGlobalService;
    private final FamiliarRepository familiarRepository;
    private final FamiliaRepository familiaRepository;
    private final PasswordEncoder passwordEncoder; 

    public SysAdminController(AdminGlobalService adminGlobalService,
            FamiliarRepository familiarRepository,
            FamiliaRepository familiaRepository, 
            PasswordEncoder passwordEncoder) {
        this.adminGlobalService = adminGlobalService;
        this.familiarRepository = familiarRepository;
        this.familiaRepository = familiaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Dashboard Central de Superusuario
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<Familia> familias = adminGlobalService.obtenerFamiliasPaginadas(page);
        model.addAttribute("familias", familias);
        model.addAttribute("currentPage", page);
        model.addAttribute("nuevaFamilia", new Familia());
        model.addAttribute("esSysAdmin", true);
        return "dashboard"; 
    }

    // ==========================================
    // CRUD DE FAMILIAS
    // ==========================================

    @PostMapping("/familia/guardar")
    public String guardarFamilia(@ModelAttribute("nuevaFamilia") Familia familia,
            RedirectAttributes redirectAttributes) {
        try {
            if (familia.getId() == null) {
                familia.setFechaCreacion(LocalDateTime.now());
                familia.setActivo(true);

                if (familia.getCodigoAcceso() == null || familia.getCodigoAcceso().trim().isEmpty()) {
                    String nombreDepurado = familia.getNombre()
                            .toUpperCase()
                            .replace("FAMILIA", "") 
                            .replaceAll("\\s+", ""); 

                    String prefijo = nombreDepurado.substring(0, Math.min(nombreDepurado.length(), 8));
                    int anioActual = java.time.Year.now().getValue();
                    familia.setCodigoAcceso(prefijo + anioActual);
                } else {
                    familia.setCodigoAcceso(familia.getCodigoAcceso().trim().toUpperCase());
                }

                familiaRepository.save(familia);
                redirectAttributes.addFlashAttribute("mensajeExito", "Familia creada correctamente.");
            } else {
                Familia familiaExistente = familiaRepository.findById(familia.getId())
                        .orElseThrow(() -> new RuntimeException("Familia no encontrada"));

                familiaExistente.setNombre(familia.getNombre());

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
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("mensajeError",
                    "Error al procesar la operación de la familia: " + e.getMessage());
        }
        return "redirect:/sys-admin/dashboard";
    }

    @GetMapping("/familia/datos/{id}")
    @ResponseBody
    public Familia obtenerDatosFamilia(@PathVariable("id") Long id) {
        return familiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Familia no encontrada"));
    }

    @PostMapping("/familia/eliminar/{id}")
    public String eliminarFamilia(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            familiaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensajeExito", "Familia eliminada con éxito del sistema.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError",
                    "No se puede eliminar la familia porque tiene usuarios o registros asociados.");
        }
        return "redirect:/sys-admin/dashboard";
    }

    // ==========================================
    // GESTIÓN DE MIEMBROS (OPTIMIZADO & CORREGIDO)
    // ==========================================

    // Ver y administrar los familiares de una familia elegida (Se renombra {id} a {familiaId})
    @GetMapping("/familia/{familiaId}/miembros")
    public String gestionarMiembros(@PathVariable("familiaId") Long familiaId, Model model) {
        List<Familiar> miembros = adminGlobalService.obtenerMiembrosPorFamilia(familiaId);
        model.addAttribute("miembros", miembros);
        model.addAttribute("familiaId", familiaId);
        model.addAttribute("tiposMembresia", TipoMembresia.values());

        Familiar nuevoMiembro = new Familiar();
        nuevoMiembro.setAdministrador(false); 
        model.addAttribute("nuevoMiembro", nuevoMiembro);

        return "miembros-detalle";
    }

    // SOLUCIÓN AL BUG: Se cambia la ruta a /familia/{familiaId}/miembros/guardar
    @PostMapping("/familia/{familiaId}/miembros/guardar")
    public String guardarMiembro(@PathVariable("familiaId") Long familiaId,
            @ModelAttribute("nuevoMiembro") Familiar familiar,
            @RequestParam(value = "isAdministradorCheck", required = false) Boolean isAdministradorCheck,
            RedirectAttributes redirectAttributes) {
        try {
            if (familiar.getId() == null) {
                familiar.setId(null); // Forzar inserción limpia si es ID autogenerado
                
                if (familiar.getPassword() != null && !familiar.getPassword().trim().isEmpty()) {
                    String encodedPassword = passwordEncoder.encode(familiar.getPassword().trim());
                    familiar.setPassword(encodedPassword);
                } else {
                    throw new RuntimeException("La contraseña temporal es obligatoria para nuevos registros.");
                }
            } else {
                // Manejo por si es edición y la contraseña viene vacía (mantener la actual)
                Familiar familiarExistente = familiarRepository.findById(familiar.getId()).orElse(null);
                if (familiarExistente != null && (familiar.getPassword() == null || familiar.getPassword().trim().isEmpty())) {
                    familiar.setPassword(familiarExistente.getPassword());
                } else if (familiar.getPassword() != null && !familiar.getPassword().trim().isEmpty()) {
                    familiar.setPassword(passwordEncoder.encode(familiar.getPassword().trim()));
                }
            } // <-- Llave de cierre del bloque ELSE (Línea 169 corregida)

            // Lógica para verificar el switch de Administrador
            boolean esAdmin = (isAdministradorCheck != null && isAdministradorCheck);
            familiar.setAdministrador(esAdmin);

            // Guardar a través del servicio con sus validaciones correspondientes
            adminGlobalService.guardarMiembroConReglas(familiaId, familiar);
            redirectAttributes.addFlashAttribute("mensajeExito", "Miembro integrado con éxito a la familia.");

        } catch (Exception e) {

        e.printStackTrace();
            redirectAttributes.addFlashAttribute("mensajeError", "Error al procesar el miembro: " + e.getMessage());
        } 
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros";

    } 

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

    @PostMapping("/familia/reasignar-admin")
    public String reasignarAdmin(@RequestParam("familiaId") Long familiaId,
            @RequestParam("nuevoAdminId") Long nuevoAdminId) {
        adminGlobalService.cambiarAdministradorDeFamilia(familiaId, nuevoAdminId);
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros";
    }

    @PostMapping("/familia/{familiaId}/miembros/eliminar/{miembroId}")
    public String eliminarMiembro(@PathVariable("familiaId") Long familiaId,
            @PathVariable("miembroId") Long miembroId,
            RedirectAttributes redirectAttributes) {
        try {
            adminGlobalService.eliminarMiembroConReglas(miembroId);
            redirectAttributes.addFlashAttribute("mensajeExito", "El miembro ha sido eliminado correctamente.");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("mensajeError", "No se pudo eliminar al miembro: " + e.getMessage());
        }
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros";
    }
}