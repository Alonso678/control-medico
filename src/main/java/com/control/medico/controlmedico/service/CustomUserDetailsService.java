package com.control.medico.controlmedico.service;

import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final FamiliarRepository familiarRepository;

    public CustomUserDetailsService(FamiliarRepository familiarRepository) {
        this.familiarRepository = familiarRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Buscamos al familiar por su columna username
        Familiar familiar = familiarRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // 2. Validación de seguridad: Verificamos que el familiar tenga un rol asignado en la BD
        if (familiar.getRole() == null || familiar.getRole().getDescripcion() == null) {
            throw new UsernameNotFoundException("El usuario " + username + " no tiene un rol asignado.");
        }

        // 3. Extraemos el String de la descripción (ej: "ROLE_ADMIN", "SYS_ADMIN")
        String nombreRol = familiar.getRole().getDescripcion();

        // 4. Mapeamos el rol asegurando el prefijo 'ROLE_' que requiere Spring Security
        // Nota: Como tus nuevos roles ya traen "ROLE_" o son "SYS_ADMIN", esto previene errores si olvidas el prefijo en BD
        String roleConPrefijo = nombreRol.startsWith("ROLE_") ? nombreRol : "ROLE_" + nombreRol;

        // 5. Retornamos el objeto User nativo de Spring Security
        return User.withUsername(familiar.getUsername())
                .password(familiar.getPassword())
                .authorities(roleConPrefijo) 
                .build();
    }
}