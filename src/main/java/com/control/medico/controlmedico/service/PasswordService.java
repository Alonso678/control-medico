package com.control.medico.controlmedico.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder;

    // Inyección por constructor
    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Convierte una contraseña en texto plano a un hash BCrypt seguro.
     */
    public String encriptarContrasena(String textoPlano) {
        if (textoPlano == null || textoPlano.trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía");
        }
        return passwordEncoder.encode(textoPlano);
    }

    /**
     * Verifica si una contraseña en texto plano coincide con el hash almacenado.
     * (Así es como validará el acceso Spring Security internamente)
     */
    public boolean verificarContrasena(String textoPlano, String hashAlmacenado) {
        return passwordEncoder.matches(textoPlano, hashAlmacenado);
    }
}
