package com.control.medico.controlmedico.controller;

import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequestMapping("/familia")
public class FamiliaController {

    private final FamiliarRepository familiarRepository;

    FamiliaController(FamiliarRepository familiarRepository) {
        this.familiarRepository = familiarRepository;
    } // Tu repositorio real de Familiares

    // 1. GUARDAR O EDITAR MIEMBRO FAMILIAR
    @PostMapping("/guardar")
    public String guardarMiembro(@ModelAttribute Familiar familiar) {
        if (familiar.getId() == null) {
            // Si es un registro totalmente nuevo, validamos si es el primero en la BD
            long conteo = familiarRepository.count();
            // Si es el primero, lo hacemos ADMIN automáticamente, si no, entra como FAMILIAR estándar
            familiar.setRole(conteo == 0 ? "ROLE_ADMIN" : "ROLE_FAMILIAR");
        } else {
            // Si es una edición, preservamos el rol que ya tenía en la base de datos
            Optional<Familiar> existente = familiarRepository.findById(familiar.getId());
            existente.ifPresent(f -> familiar.setRole(f.getRole()));
        }
        
        familiarRepository.save(familiar);
        return "redirect:/"; // Redirige al Dashboard principal
    }

    // 2. PROCESO DE HEREDAR EL ROL ÚNICO DE ADMINISTRADOR
    @PostMapping("/heredar/{id}")
    @Transactional
    public String heredarAdministrador(@PathVariable("id") Long nuevoAdminId) {
        // A. Buscamos si existe alguien con el rol de ADMIN actualmente y lo bajamos a miembro común
        // Nota: Puedes hacer un método personalizado en tu repositorio o buscarlo así de manera general:
        familiarRepository.findAll().stream()
            .filter(f -> "ROLE_ADMIN".equalsIgnoreCase(f.getRole()))
            .forEach(f -> {
                f.setRole("ROLE_FAMILIAR");
                familiarRepository.save(f);
            });
        
        // B. Le otorgamos el rol de ADMIN de forma única al nuevo ID seleccionado
        Optional<Familiar> nuevoAdmin = familiarRepository.findById(nuevoAdminId);
        if (nuevoAdmin.isPresent()) {
            Familiar familiar = nuevoAdmin.get();
            familiar.setRole("ROLE_ADMIN"); // Asignamos el rol alto
            familiarRepository.save(familiar);
        }
        
        // Al quitarle el rol al usuario actual, lo mandamos a cerrar sesión para aplicar cambios
        return "redirect:/login?logout=true"; 
    }

    // 3. ELIMINAR UN MIEMBRO
    @PostMapping("/eliminar/{id}")
    public String eliminarMiembro(@PathVariable("id") Long id) {
        Optional<Familiar> familiar = familiarRepository.findById(id);
        // Regla de seguridad: No dejamos que se elimine al administrador activo
        // directamente
        if (familiar.isPresent() && !"ROLE_ADMIN".equalsIgnoreCase(familiar.get().getRole())) {
            familiarRepository.deleteById(id);
        }
        return "redirect:/";
    }

}