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
@RequestMapping("/sys-admin") // Prefijo base para este controlador
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

    // ==========================================
    // ENDPOINTS EXCLUSIVOS DE SYS_ADMIN
    // ==========================================

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('SYS_ADMIN')") // 🔐 Protegido individualmente
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<Familia> familias = adminGlobalService.obtenerFamiliasPaginadas(page);
        model.addAttribute("familias", familias);
        model.addAttribute("currentPage", page);
        model.addAttribute("nuevaFamilia", new Familia());
        model.addAttribute("esSysAdmin", true);
        return "dashboard"; 
    }

    @PostMapping("/familia/guardar")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public String guardarFamilia(@ModelAttribute("nuevaFamilia") Familia familia, RedirectAttributes redirectAttributes) {
        try {
            if (familia.getId() == null) {
                familia.setFechaCreacion(LocalDateTime.now());
                familia.setActivo(true);
                if (familia.getCodigoAcceso() == null || familia.getCodigoAcceso().trim().isEmpty()) {
                    String nombreDepurado = familia.getNombre().toUpperCase().replace("FAMILIA", "").replaceAll("\\s+", ""); 
                    String prefijo = nombreDepurado.substring(0, Math.min(nombreDepurado.length(), 8));
                    int anioActual = java.time.Year.now().getValue();
                    familia.setCodigoAcceso(prefijo + anioActual);
                } else {
                    familia.setCodigoAcceso(familia.getCodigoAcceso().trim().toUpperCase());
                }
                familiaRepository.save(familia);
                redirectAttributes.addFlashAttribute("mensajeExito", "Familia creada correctamente.");
            } else {
                Familia familiaExistente = familiaRepository.findById(familia.getId()).orElseThrow(() -> new RuntimeException("Familia no encontrada"));
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
            redirectAttributes.addFlashAttribute("mensajeError", "Error al procesar: " + e.getMessage());
        }
        return "redirect:/sys-admin/dashboard";
    }

    @GetMapping("/familia/datos/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public Familia obtenerDatosFamilia(@PathVariable("id") Long id) {
        return familiaRepository.findById(id).orElseThrow(() -> new RuntimeException("Familia no encontrada"));
    }

    @PostMapping("/familia/eliminar/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public String eliminarFamilia(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            familiaRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("mensajeExito", "Familia eliminada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", "No se puede eliminar.");
        }
        return "redirect:/sys-admin/dashboard";
    }

    // ==========================================
    // GESTIÓN DE MIEMBROS COMPARITDA (SYS_ADMIN y ADMIN)
    // ==========================================

    @GetMapping("/familia/{familiaId}/miembros")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')") // 🔓 Permite la entrada a ambos
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

    @PostMapping("/familia/{familiaId}/miembros/guardar")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    public String guardarMiembro(@PathVariable("familiaId") Long familiaId,
            @ModelAttribute("nuevoMiembro") Familiar familiar,
            @RequestParam(value = "isAdministradorCheck", required = false) Boolean isAdministradorCheck,
            RedirectAttributes redirectAttributes) {
        try {
            if (familiar.getId() == null) {
                familiar.setId(null); 
                if (familiar.getPassword() != null && !familiar.getPassword().trim().isEmpty()) {
                    familiar.setPassword(passwordEncoder.encode(familiar.getPassword().trim()));
                } else {
                    throw new RuntimeException("La contraseña es obligatoria.");
                }
            } else {
                Familiar familiarExistente = familiarRepository.findById(familiar.getId()).orElse(null);
                if (familiarExistente != null && (familiar.getPassword() == null || familiar.getPassword().trim().isEmpty())) {
                    familiar.setPassword(familiarExistente.getPassword());
                } else if (familiar.getPassword() != null && !familiar.getPassword().trim().isEmpty()) {
                    familiar.setPassword(passwordEncoder.encode(familiar.getPassword().trim()));
                }
            } 

            boolean esAdmin = (isAdministradorCheck != null && isAdministradorCheck);
            familiar.setAdministrador(esAdmin);

            adminGlobalService.guardarMiembroConReglas(familiaId, familiar);
            redirectAttributes.addFlashAttribute("mensajeExito", "Miembro guardado con éxito.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error: " + e.getMessage());
        } 
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros";
    } 

    @PostMapping("/miembro/actualizar-membresia")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    public String actualizarMembresia(@RequestParam("miembroId") Long miembroId,
            @RequestParam("tipoMembresia") TipoMembresia tipoMembresia,
            @RequestParam("familiaId") Long familiaId) {
        Familiar familiar = familiarRepository.findById(miembroId).orElseThrow(() -> new RuntimeException("No encontrado")); 
        familiar.setTipoMembresia(tipoMembresia); 
        familiarRepository.save(familiar); 
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros"; 
    }

    @PostMapping("/familia/reasignar-admin")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    public String reasignarAdmin(@RequestParam("familiaId") Long familiaId, @RequestParam("nuevoAdminId") Long nuevoAdminId) {
        adminGlobalService.cambiarAdministradorDeFamilia(familiaId, nuevoAdminId);
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros";
    }

    @PostMapping("/familia/{familiaId}/miembros/eliminar/{miembroId}")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    public String eliminarMiembro(@PathVariable("familiaId") Long familiaId,
            @PathVariable("miembroId") Long miembroId,
            RedirectAttributes redirectAttributes) {
        try {
            adminGlobalService.eliminarMiembroConReglas(miembroId);
            redirectAttributes.addFlashAttribute("mensajeExito", "Miembro eliminado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", "Error: " + e.getMessage());
        }
        return "redirect:/sys-admin/familia/" + familiaId + "/miembros";
    }

    // ==========================================
    // ENDPOINT PARA EL ROLE_ADMIN LOGUEADO
    // ==========================================
    
    // Ruta final: /sys-admin/mi-familia/miembros
    @GetMapping("/mi-familia/miembros")
    @PreAuthorize("hasRole('ADMIN')")
    public String gestionarMiembrosPropios(org.springframework.security.core.Authentication authentication, Model model) {
        String username = authentication.getName();
        Familiar adminLogueado = familiarRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Administrador no encontrado en el sistema"));
        
        if (adminLogueado.getFamilia() == null) {
            throw new RuntimeException("El usuario actual no tiene ninguna familia asignada.");
        }
        
        Long familiaId = adminLogueado.getFamilia().getId();

        // Reutiliza de forma transparente el método de arriba pasándole el ID correcto detectado en base de datos.
        return gestionarMiembros(familiaId, model);
    }
}