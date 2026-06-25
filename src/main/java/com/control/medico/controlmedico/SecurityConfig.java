package com.control.medico.controlmedico;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Deshabilitar CSRF temporalmente para evitar bloqueos en formularios
            .csrf(csrf -> csrf.disable())
            // Permitir el acceso total a todas las rutas del sistema por ahora
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            // Permitir que se sigan usando formularios si se requiere en el futuro
            .formLogin(form -> form.permitAll());

        return http.build();
    }
}
