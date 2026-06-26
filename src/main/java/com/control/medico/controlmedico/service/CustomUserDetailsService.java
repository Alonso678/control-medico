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
        // Buscamos al familiar por su columna username en pgAdmin
        Familiar familiar = familiarRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // Mapeamos el rol asegurando el prefijo 'ROLE_' que requiere Spring Security (ej. ROLE_ADMIN o ROLE_FAMILIAR)
        String roleConPrefijo = familiar.getRole().startsWith("ROLE_") ? familiar.getRole() : "ROLE_" + familiar.getRole();

        // Retornamos el objeto User nativo de Spring Security con sus credenciales de la BD
        return User.withUsername(familiar.getUsername())
                .password(familiar.getPassword())
                .authorities(roleConPrefijo) 
                .build();
    }
}