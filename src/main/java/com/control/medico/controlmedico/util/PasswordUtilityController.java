package com.control.medico.controlmedico.util;

import com.control.medico.controlmedico.service.PasswordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/util")
public class PasswordUtilityController {

    private final PasswordService passwordService;

    public PasswordUtilityController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    /**
     * Endpoint para generar el Hash.
     * POST http://localhost:8080/api/util/encriptar
     * Body (JSON): { "password": "mi_clave_actual" }
     */
    @PostMapping("/encriptar")
    public ResponseEntity<Map<String, String>> generarHash(@RequestBody Map<String, String> request) {
        String textoPlano = request.get("password");
        String hashGenerado = passwordService.encriptarContrasena(textoPlano);

        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("textoPlano", textoPlano);
        respuesta.put("hashBcrypt", hashGenerado);

        return ResponseEntity.ok(respuesta);
    }
}
