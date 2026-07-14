package com.control.medico.controlmedico.controller;

import com.control.medico.controlmedico.model.Familia;
import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.TipoMembresia;
import com.control.medico.controlmedico.service.AdminGlobalService;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/sys-admin")
@PreAuthorize("hasRole('SYS_ADMIN')") // Protección estricta a nivel de clase
public class SysAdminController {

    private final AdminGlobalService adminGlobalService;
    private final FamiliarRepository familiarRepository;

    public SysAdminController(AdminGlobalService adminGlobalService, FamiliarRepository familiarRepository) {
        this.adminGlobalService = adminGlobalService;
        this.familiarRepository = familiarRepository;
    }

    // Dashboard Central de Superusuario
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<Familia> familias = adminGlobalService.obtenerFamiliasPaginadas(page);
        model.addAttribute("familias", familias);
        model.addAttribute("currentPage", page);
        return "dashboard";
    }

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
