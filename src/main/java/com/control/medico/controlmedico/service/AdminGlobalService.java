package com.control.medico.controlmedico.service;

import com.control.medico.controlmedico.model.Familia;
import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.Rol;
import com.control.medico.controlmedico.repository.FamiliaRepository;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import com.control.medico.controlmedico.repository.RolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminGlobalService { // <-- Asegurar que se llama exactamente así

    private final FamiliaRepository familiaRepository;
    private final FamiliarRepository familiarRepository;
    private final RolRepository rolRepository;

    public AdminGlobalService(FamiliaRepository familiaRepository, 
                              FamiliarRepository familiarRepository, 
                              RolRepository rolRepository) {
        this.familiaRepository = familiaRepository;
        this.familiarRepository = familiarRepository;
        this.rolRepository = rolRepository;
    }

    public Page<Familia> obtenerFamiliasPaginadas(int pagina) {
        return familiaRepository.findAllByOrderByFechaCreacionDesc(PageRequest.of(pagina, 10));
    }

    public List<Familiar> obtenerMiembrosPorFamilia(Long familiaId) {
        return familiarRepository.findByFamiliaId(familiaId);
    }

    @Transactional
    public void cambiarAdministradorDeFamilia(Long familiaId, Long nuevoAdminId) {
        Rol rolFamiliar = rolRepository.findByDescripcion("ROLE_FAMILIAR")
                .orElseThrow(() -> new RuntimeException("Rol ROLE_FAMILIAR no configurado"));
        Rol rolAdmin = rolRepository.findByDescripcion("ROLE_ADMIN")
                .orElseThrow(() -> new RuntimeException("Rol ROLE_ADMIN no configurado"));

        // Degradamos a cualquier ADMIN viejo de ESTA familia específica
        familiarRepository.findByFamiliaId(familiaId).stream()
                .filter(f -> f.getRole() != null && "ROLE_ADMIN".equalsIgnoreCase(f.getRole().getDescripcion()))
                .forEach(f -> {
                    f.setRole(rolFamiliar);
                    familiarRepository.save(f);
                });

        // Promovemos al nuevo usuario seleccionado como Administrador de su familia
        Familiar nuevoAdmin = familiarRepository.findById(nuevoAdminId)
                .orElseThrow(() -> new RuntimeException("Miembro no encontrado"));
        nuevoAdmin.setRole(rolAdmin);
        familiarRepository.save(nuevoAdmin);
    }
    
    @Transactional
    public void cambiarEstatusFamilia(Long familiaId, Boolean estatus) {
        Familia familia = familiaRepository.findById(familiaId)
                .orElseThrow(() -> new RuntimeException("Familia no encontrada"));
        familia.setActivo(estatus);
        familiaRepository.save(familia);
    }
}
