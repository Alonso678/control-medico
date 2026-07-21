package com.control.medico.controlmedico.service;

import com.control.medico.controlmedico.model.Familia;
import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.Rol;
import com.control.medico.controlmedico.repository.FamiliaRepository;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import com.control.medico.controlmedico.repository.RolRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Service
public class AdminGlobalService { // <-- Asegurar que se llama exactamente así

    private final FamiliaRepository familiaRepository;
    private final FamiliarRepository familiarRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminGlobalService(FamiliaRepository familiaRepository,
            FamiliarRepository familiarRepository,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder) {
        this.familiaRepository = familiaRepository;
        this.familiarRepository = familiarRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
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

        // 1. Degradamos a cualquier ADMIN viejo de ESTA familia específica basándonos en el flag booleano
        familiarRepository.findByFamiliaId(familiaId).stream()
                .filter(f -> f.getAdministrador() != null && f.getAdministrador())
                .forEach(f -> {
                    f.setAdministrador(false); // <-- CORRECCIÓN: Apaga el flag booleano en la BD
                    f.setRole(rolFamiliar);    // <-- Cambia el role_id a 2 (ROLE_FAMILIAR)
                    familiarRepository.save(f);
                });

        // 2. Promovemos al nuevo usuario seleccionado como Administrador de su familia
        Familiar nuevoAdmin = familiarRepository.findById(nuevoAdminId)
                .orElseThrow(() -> new RuntimeException("Miembro no encontrado"));
        
        nuevoAdmin.setAdministrador(true); // <-- CORRECCIÓN: Enciende el flag booleano en la BD
        nuevoAdmin.setRole(rolAdmin);      // <-- Cambia el role_id a 3 (ROLE_ADMIN)
        familiarRepository.save(nuevoAdmin);
    }

    @Transactional
    public void cambiarEstatusFamilia(Long familiaId, Boolean estatus) {
        Familia familia = familiaRepository.findById(familiaId)
                .orElseThrow(() -> new RuntimeException("Familia no encontrada"));
        familia.setActivo(estatus);
        familiaRepository.save(familia);
    }

    @Transactional
    public void guardarMiembroConReglas(Long familiaId, Familiar familiar) {
        // 1. Obtener y vincular la familia objetivo
        Familia familia = familiaRepository.findById(familiaId)
                .orElseThrow(() -> new EntityNotFoundException("La familia especificada con ID " + familiaId + " no existe."));
        familiar.setFamilia(familia);

        // 2. CORREGIDO: Control de Excepciones robusto en la Edición (Cambio 4)
        if (familiar.getId() == null) {
            if (familiar.getPassword() != null && !familiar.getPassword().trim().isEmpty()) {
                familiar.setPassword(passwordEncoder.encode(familiar.getPassword().trim()));
            } else {
                familiar.setPassword(passwordEncoder.encode(familiar.getUsername().trim()));
            }
        } else {
            // AJUSTE: Si el ID existe pero no está en la BD, lanza EntityNotFoundException de inmediato
            Familiar familiarExistente = familiarRepository.findById(familiar.getId())
                    .orElseThrow(() -> new EntityNotFoundException("El miembro familiar con ID " + familiar.getId() + " no existe."));
            
            if (familiar.getPassword() == null || familiar.getPassword().trim().isEmpty()) {
                // Conserva la contraseña encriptada actual
                familiar.setPassword(familiarExistente.getPassword());
            } else {
                // Si cambiaron la contraseña en el formulario, se encripta la nueva
                familiar.setPassword(passwordEncoder.encode(familiar.getPassword().trim()));
            }
        }

        // 3. Cargar las entidades de Roles desde la base de datos
        Rol rolAdmin = rolRepository.findByDescripcion("ROLE_ADMIN")
                .orElseThrow(() -> new EntityNotFoundException("Configuración ausente: Falta el rol ROLE_ADMIN"));
        Rol rolFamiliar = rolRepository.findByDescripcion("ROLE_FAMILIAR")
                .orElseThrow(() -> new EntityNotFoundException("Configuración ausente: Falta el rol ROLE_FAMILIAR"));

        // 4. Aplicar regla estricta de un solo administrador
        if (familiar.getAdministrador()) {
            List<Familiar> integrantesActuales = familiarRepository.findByFamiliaId(familiaId);
            for (Familiar integrante : integrantesActuales) {
                // Evitamos degradar al mismo miembro si es una actualización de sus propios datos
                if (integrante.getAdministrador() && !integrante.getId().equals(familiar.getId())) {
                    integrante.setAdministrador(false);
                    integrante.setRole(rolFamiliar);
                    familiarRepository.save(integrante);
                }
            }
            familiar.setRole(rolAdmin);
        } else {
            familiar.setRole(rolFamiliar);
        }

        // 5. Guardar el integrante
        familiarRepository.save(familiar);
    }

    @Transactional
    public void eliminarMiembroConReglas(Long miembroId) {
        Familiar familiar = familiarRepository.findById(miembroId)
                .orElseThrow(() -> new EntityNotFoundException("El miembro a eliminar con ID " + miembroId + " no existe."));
        familiarRepository.delete(familiar);
    }
}
