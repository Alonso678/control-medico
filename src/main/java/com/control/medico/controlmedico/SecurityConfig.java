package com.control.medico.controlmedico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("[LOG-SECURITY-INIT] ⚙️ Cargando la cadena de filtros de Spring Security...");

        http
                // Desactivamos CSRF temporalmente para pruebas locales
                .csrf(csrf -> csrf.disable())
                
                // 1. FILTRO DE LOGS PERSONALIZADO (Muestra qué URL entra al sistema de seguridad)
                .addFilterBefore(new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
                            throws IOException, jakarta.servlet.ServletException {
                        HttpServletRequest req = (HttpServletRequest) request;
                        log.info("[LOG-HTTP-REQUEST] 🌐 Entrando Petición -> Método: [{}], URI: [{}]", req.getMethod(), req.getRequestURI());
                        chain.doFilter(request, response);
                    }
                }, BasicAuthenticationFilter.class)

                // 2. CONFIGURACIÓN DE AUTORIZACIONES
                .authorizeHttpRequests(auth -> auth
                        // Recursos públicos indispensables
                        .requestMatchers("/login", "/css/**", "/js/**").permitAll()

                        // 🔄 MODIFICACIÓN AQUÍ: Permitimos el paso a ambos roles para las rutas de administración compartidas
                        .requestMatchers("/sys-admin/**").hasAnyAuthority("ROLE_SYS_ADMIN", "ROLE_ADMIN")
                        .requestMatchers("/familia/**").hasAuthority("ROLE_ADMIN")
                        
                        // La raíz "/" debe estar autenticada para permitir que el controlador decida la redirección por rol
                        .requestMatchers("/").authenticated()
                        .requestMatchers("/reporte/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_FAMILIAR")

                        // Cualquier otra ruta interna requiere estar logueado
                        .anyRequest().authenticated())
                
                // 3. LOGS DE INICIO DE SESIÓN EXITOSO
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(customSuccessHandler()) // Manejador con logs integrados
                        .permitAll())
                
                // 4. LOGS DE ACCESOS DENEGADOS (Para atrapar los errores 403)
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(customAccessDeniedHandler()))
                
                .logout(logout -> logout.permitAll());

        return http.build();
    }

    // Bean del manejador de éxito en el login
    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            String username = authentication.getName();
            Object authorities = authentication.getAuthorities();
            log.info("[LOG-SECURITY-SUCCESS] 🎉 Autenticación EXITOSA. Usuario: '{}' | Authorities asignadas: {}", username, authorities);
            
            // Decisión inteligente y limpia de redirección basada en la Authority textual
            if (authorities.toString().contains("ROLE_SYS_ADMIN")) {
                log.info("[LOG-SECURITY-SUCCESS] 🔀 Redireccionando automáticamente a /sys-admin/dashboard");
                response.sendRedirect("/sys-admin/dashboard");
            } else {
                log.info("[LOG-SECURITY-SUCCESS] 🔀 Redireccionando automáticamente al Home raíz (/)");
                response.sendRedirect("/");
            }
        };
    }

    // Bean para capturar e imprimir logs de los errores 403
    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.error("[LOG-SECURITY-403] ❌ ACCESO RECHAZADO (403 Forbidden) para la URL: '{}'", request.getRequestURI());
            log.error("[LOG-SECURITY-403] 💡 Detalle del rechazo: {}", accessDeniedException.getMessage());
            response.sendRedirect("/login?error=true");
        };
    }

    @SuppressWarnings("deprecation")
    @Bean
    public PasswordEncoder passwordEncoder() {
        return org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance();
    }
}