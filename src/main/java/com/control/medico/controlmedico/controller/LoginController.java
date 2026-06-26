package com.control.medico.controlmedico.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login"; // Debe coincidir con el nombre exacto de tu archivo login.html en templates
    }
}
