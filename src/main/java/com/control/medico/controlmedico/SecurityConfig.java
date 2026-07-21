package com.control.medico.controlmedico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
                .csrf(csrf -> csrf.disable())
                
                // 1. FILTRO DE LOGS PERSONALIZADO
                .addFilterBefore(new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
                            throws IOException, jakarta.servlet.ServletException {
                        HttpServletRequest req = (HttpServletRequest) request;
                        log.info("[LOG-HTTP-REQUEST] 🌐 Entrando Petición -> Método: [{}], URI: [{}]", req.getMethod(), req.getRequestURI());
                        chain.doFilter(request, response);
                    }
                }, BasicAuthenticationFilter.class)

                // 2. CONFIGURACIÓN DE AUTORIZACIONES (Cambio 2 Validado)
                .authorizeHttpRequests(auth -> auth
                        // 1. Recursos públicos indispensables
                        .requestMatchers("/login", "/css/**", "/js/**", "/error").permitAll()

                        // 2. Permitir el GET y el POST para la gestión de miembros a ROLE_ADMIN
                        .requestMatchers("/sys-admin/mi-familia/**").hasAnyAuthority("ROLE_ADMIN")
                        // 🚀 NUEVA LÍNEA: Permite que el ADMIN guarde los cambios de los miembros de su
                        // familia
                        .requestMatchers("/sys-admin/familia/*/miembros/guardar")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_SYS_ADMIN")

                        // 3. Aislamiento estricto de la Consola Global (Solo SysAdmin)
                        .requestMatchers("/sys-admin/**").hasAuthority("ROLE_SYS_ADMIN")

                        // 4. Entorno familiar compartido
                        .requestMatchers("/").hasAnyAuthority("ROLE_ADMIN", "ROLE_FAMILIAR")
                        .requestMatchers("/familia/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_FAMILIAR")
                        .requestMatchers("/reporte/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_FAMILIAR")
                        .requestMatchers("/movimientos/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_FAMILIAR")

                        .requestMatchers("/api/util/encriptar").permitAll()
                        .anyRequest().authenticated())
                
                // 3. CONTROL DE INICIO DE SESIÓN
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(customSuccessHandler()) 
                        .permitAll())
                
                // 4. MANEJO DE ACCESOS DENEGADOS 
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(customAccessDeniedHandler()))
                
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll());

        return http.build();
    }

    // Bean del manejador de éxito en el login
    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            String username = authentication.getName();
            var authorities = authentication.getAuthorities();
            log.info("[LOG-SECURITY-SUCCESS] 🎉 Autenticación EXITOSA. Usuario: '{}' | Authorities: {}", username, authorities);
            
            boolean isSysAdmin = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_SYS_ADMIN"));
            
            if (isSysAdmin) {
                log.info("[LOG-SECURITY-SUCCESS] 🔀 Redireccionando a Consola Global [/sys-admin/dashboard]");
                response.sendRedirect("/sys-admin/dashboard");
            } else {
                // Redirección limpia a la raíz del proyecto para ADMIN y FAMILIAR
                log.info("[LOG-SECURITY-SUCCESS] 🔀 Redireccionando a Panel Unificado [/]");
                response.sendRedirect("/");
            }
        };
    }

    // Bean para capturar errores 403 de forma segura
    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.error("[LOG-SECURITY-403] ❌ ACCESO RECHAZADO (403 Forbidden) en: '{}'", request.getRequestURI());
            log.error("[LOG-SECURITY-403] 💡 Razón: {}", accessDeniedException.getMessage());
            response.sendRedirect("/?errorAccess=true");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}