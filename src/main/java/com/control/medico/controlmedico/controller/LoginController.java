package com.control.medico.controlmedico.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String mostrarLogin(HttpServletRequest request,
            @RequestParam(value = "error", required = false) String errorParam) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            Exception exception = (Exception) session.getAttribute("SPRING_SECURITY_LAST_EXCEPTION");

            // Si es un error por cuenta deshabilitada y NO venimos ya de la redirección
            if (exception instanceof DisabledException && !"inactivo".equals(errorParam)) {

                // Opcional: Limpiamos la excepción de la sesión para evitar remanentes
                session.removeAttribute("SPRING_SECURITY_LAST_EXCEPTION");

                // Redirigimos de forma segura controlada
                return "redirect:/login?error=inactivo";
            }
        }

        // Si ya viene el parámetro ?error=inactivo o no hay errores, renderiza la vista
        // en paz
        return "login";
    }
}
