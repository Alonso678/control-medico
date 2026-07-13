package com.control.medico.controlmedico.controller;

import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.Rol;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import com.control.medico.controlmedico.repository.RolRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequestMapping("/familia")
public class FamiliaController {

    private final FamiliarRepository familiarRepository;
    private final RolRepository rolRepository; // 1. Inyectamos el nuevo repositorio

    // Actualizamos el constructor
    FamiliaController(FamiliarRepository familiarRepository, RolRepository rolRepository) {
        this.familiarRepository = familiarRepository;
        this.rolRepository = rolRepository;
    }

    // 1. GUARDAR O EDITAR MIEMBRO FAMILIAR
    @PostMapping("/guardar")
    public String guardarMiembro(@ModelAttribute Familiar familiar) {
        if (familiar.getId() == null) {
            long conteo = familiarRepository.count();
            
            // Buscamos el objeto Rol correspondiente desde la BD
            Rol rolAsignado = rolRepository.findByDescripcion(conteo == 0 ? "ROLE_ADMIN" : "ROLE_FAMILIAR")
                    .orElseThrow(() -> new RuntimeException("Error: El Rol especificado no existe en la base de datos."));
            
            familiar.setRole(rolAsignado);
        } else {
            // Preservamos el rol existente cargándolo de la base de datos
            Optional<Familiar> existente = familiarRepository.findById(familiar.getId());
            existente.ifPresent(f -> familiar.setRole(f.getRole()));
        }
        
        familiarRepository.save(familiar);
        return "redirect:/";
    }

    // 2. PROCESO DE HEREDAR EL ROL ÚNICO DE ADMINISTRADOR
    @PostMapping("/heredar/{id}")
    @Transactional
    public String heredarAdministrador(@PathVariable("id") Long nuevoAdminId) {
        // Obtenemos las entidades de los roles desde la BD para asegurar consistencia
        Rol rolFamiliar = rolRepository.findByDescripcion("ROLE_FAMILIAR")
                .orElseThrow(() -> new RuntimeException("Rol ROLE_FAMILIAR no encontrado"));
        Rol rolAdmin = rolRepository.findByDescripcion("ROLE_ADMIN")
                .orElseThrow(() -> new RuntimeException("Rol ROLE_ADMIN no encontrado"));

        // A. Buscamos si existe alguien con el rol de ADMIN actualmente y lo bajamos a familiar
        familiarRepository.findAll().stream()
            .filter(f -> f.getRole() != null && "ROLE_ADMIN".equalsIgnoreCase(f.getRole().getDescripcion()))
            .forEach(f -> {
                f.setRole(rolFamiliar);
                familiarRepository.save(f);
            });
        
        // B. Le otorgamos el rol de ADMIN de forma única al nuevo ID seleccionado
        Optional<Familiar> nuevoAdmin = familiarRepository.findById(nuevoAdminId);
        if (nuevoAdmin.isPresent()) {
            Familiar familiar = nuevoAdmin.get();
            familiar.setRole(rolAdmin);
            familiarRepository.save(familiar);
        }
        
        return "redirect:/login?logout=true"; 
    }

    // 3. ELIMINAR UN MIEMBRO
    @PostMapping("/eliminar/{id}")
    public String eliminarMiembro(@PathVariable("id") Long id) {
        Optional<Familiar> familiar = familiarRepository.findById(id);
        
        // Modificación de la regla de seguridad evaluando la descripción del objeto Rol
        if (familiar.isPresent() && 
            familiar.get().getRole() != null && 
            !"ROLE_ADMIN".equalsIgnoreCase(familiar.get().getRole().getDescripcion())) {
            
            familiarRepository.deleteById(id);
        }
        return "redirect:/";
    }
}