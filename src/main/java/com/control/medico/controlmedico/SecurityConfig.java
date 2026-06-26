package com.control.medico.controlmedico;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 1. Las rutas del login y recursos estáticos son públicas
                        .requestMatchers("/login", "/css/**", "/js/**").permitAll()

                        // 2. Toda la gestión familiar (guardar, eliminar, heredar) queda EXCLUSIVA para
                        // el ADMIN
                        .requestMatchers("/familia/**").hasRole("ADMIN")

                        // 3. El Dashboard principal y la descarga de reportes individuales son para
                        // ambos roles
                        .requestMatchers("/", "/reporte/**").hasAnyRole("ADMIN", "FAMILIAR")

                        // Cualquier otra petición requiere autenticación básica
                        .anyRequest().authenticated())
                // ... el resto de tu configuración de formLogin y logout
                .formLogin(form -> form.loginPage("/login").permitAll())
                .logout(logout -> logout.permitAll());

        return http.build();
    }

    @SuppressWarnings("deprecation")
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Esto quita el warning y permite seguir validando en texto plano desde pgAdmin
        return org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance();
    }
}
