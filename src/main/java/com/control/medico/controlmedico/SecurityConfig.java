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
                        // 1. Permitimos el acceso 100% libre a los assets estáticos y explícitamente a
                        // la ruta de login
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/login").permitAll()
                        // 2. Cualquier otra petición al sistema requerirá autenticación obligatoria
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login") // Define nuestra vista personalizada
                        .loginProcessingUrl("/login") // URL interna de Spring para procesar el POST de credenciales
                        .defaultSuccessUrl("/", true) // Redirección al Dashboard tras iniciar sesión con éxito
                        .failureUrl("/login?error=true") // Redirección si las credenciales fallan
                        .permitAll() // Asegura que el flujo del formulario de login sea público
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll());

        return http.build();
    }

    @SuppressWarnings("deprecation")
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Esto quita el warning y permite seguir validando en texto plano desde pgAdmin
        return org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance();
    }
}
