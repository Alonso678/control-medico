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
                .orElseThrow(() -> new RuntimeException("La familia especificada no existe."));
        familiar.setFamilia(familia);

        // 2. CORREGIDO: Respetar la contraseña que se ingresó en el modal y ENCRIPTARLA
        if (familiar.getId() == null) {
            // Validamos que se haya escrito algo en el campo "Contraseña Temporal"
            if (familiar.getPassword() != null && !familiar.getPassword().trim().isEmpty()) {
                // Encriptamos la clave escrita en el frontend antes de persistir en PostgreSQL
                familiar.setPassword(passwordEncoder.encode(familiar.getPassword().trim()));
            } else {
                // Si por alguna razón viene vacía, podemos dejar como respaldo el username encriptado
                familiar.setPassword(passwordEncoder.encode(familiar.getUsername().trim()));
            }
        } else {
            // Manejo en caso de edición (no modificar contraseña si no fue enviada)
            Familiar familiarExistente = familiarRepository.findById(familiar.getId()).orElse(null);
            if (familiarExistente != null && (familiar.getPassword() == null || familiar.getPassword().trim().isEmpty())) {
                familiar.setPassword(familiarExistente.getPassword());
            } else if (familiar.getPassword() != null && !familiar.getPassword().trim().isEmpty()) {
                familiar.setPassword(passwordEncoder.encode(familiar.getPassword().trim()));
            }
        }

        // 3. Cargar las entidades de Roles desde la base de datos
        Rol rolAdmin = rolRepository.findByDescripcion("ROLE_ADMIN")
                .orElseThrow(() -> new RuntimeException("Configuración ausente: Falta el rol ROLE_ADMIN"));
        Rol rolFamiliar = rolRepository.findByDescripcion("ROLE_FAMILIAR")
                .orElseThrow(() -> new RuntimeException("Configuración ausente: Falta el rol ROLE_FAMILIAR"));

        // 4. Aplicar regla estricta de un solo administrador
        if (familiar.getAdministrador()) {
            // Buscamos si la familia ya cuenta con un miembro administrador activo
            List<Familiar> integrantesActuales = familiarRepository.findByFamiliaId(familiaId);
            for (Familiar integrante : integrantesActuales) {
                if (integrante.getAdministrador()) {
                    // "Degradamos" al admin anterior para conservar la unicidad
                    integrante.setAdministrador(false);
                    integrante.setRole(rolFamiliar);
                    familiarRepository.save(integrante);
                }
            }
            // Asignamos el rol administrativo al nuevo miembro
            familiar.setRole(rolAdmin);
        } else {
            // Todos los demás miembros entran forzosamente como familiares comunes
            familiar.setRole(rolFamiliar);
        }

        // 5. Guardar el nuevo integrante
        familiarRepository.save(familiar);
    }

    @Transactional
    public void eliminarMiembroConReglas(Long miembroId) {
        Familiar familiar = familiarRepository.findById(miembroId)
                .orElseThrow(() -> new RuntimeException("El miembro a eliminar no existe."));

        // Regla de seguridad opcional: Evitar que la familia se quede sin miembros
        // o validar si el que se elimina es el último administrador.
        if (familiar.getAdministrador()) {
            // Nota: Si se elimina al admin, se podría lanzar una advertencia
            // o simplemente permitirlo y que el SysAdmin asigne a otro después.
        }

        familiarRepository.delete(familiar);
    }
}
