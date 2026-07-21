package com.control.medico.controlmedico.service;

import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service("customUserDetailsService")
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);
    private final FamiliarRepository familiarRepository;

    public CustomUserDetailsService(FamiliarRepository familiarRepository) {
        this.familiarRepository = familiarRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("[LOG-LOGIN] ──> Iniciando intento de autenticación para el usuario: '{}'", username);

        // 1. Buscar el usuario en la base de datos
        Familiar familiar = familiarRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("[LOG-LOGIN] ❌ ERROR: El usuario '{}' no existe en la base de datos.", username);
                    return new UsernameNotFoundException("Usuario no encontrado: " + username);
                });

        log.info("[LOG-LOGIN] ✔️ Usuario localizado con éxito en BD.");

        // 2. EXTRAER EL TEXTO DE LA DESCRIPCIÓN DEL ROL (Evita el Rol@250e82f)
        String nombreRol = "";
        if (familiar.getRole() != null) {
            // Asumiendo que tu entidad Rol tiene el método getDescripcion()
            nombreRol = familiar.getRole().getDescripcion();
        }

        log.info("[LOG-LOGIN] 👉 ID: {}, Nombre: '{}', Rol extraído (Texto): '{}'",
                familiar.getId(), familiar.getNombre(), nombreRol);

        if (nombreRol == null || nombreRol.trim().isEmpty()) {
            log.warn("[LOG-LOGIN] ⚠️ ADVERTENCIA: La descripción del rol para '{}' está vacía en la BD.", username);
        }

        // 3. Crear la autoridad limpia para Spring Security
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(nombreRol);
        log.info("[LOG-LOGIN] ⚙️ Autoridad asignada a Spring Security: '{}'", authority.getAuthority());

        // 3.5. DETERMINAR EL ESTADO OPERATIVO DE LA FAMILIA
        boolean cuentaHabilitada = true;
        
        // Evaluamos si el familiar pertenece a una familia y si está activa
        if (familiar.getFamilia() != null) {
            // Si la familia tiene activo = false, 'cuentaHabilitada' será false
            cuentaHabilitada = Boolean.TRUE.equals(familiar.getFamilia().getActivo());
            
            if (!cuentaHabilitada) {
                log.warn("[LOG-LOGIN] ⛔ ACCESO DENEGADO: El usuario '{}' pertenece a la familia '{}' la cual se encuentra INACTIVA.", 
                        username, familiar.getFamilia().getNombre());
            }
        }

        // 4. Retornar el User de Spring Security con constructor extendido
        return new User(
                familiar.getUsername(),
                familiar.getPassword(),
                cuentaHabilitada,       // enabled: aquí se bloquea si la familia está inactiva
                true,                   // accountNonExpired
                true,                   // credentialsNonExpired
                true,                   // accountNonLocked
                Collections.singletonList(authority)
        );
    }
}