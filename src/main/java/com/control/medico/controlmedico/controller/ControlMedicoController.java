package com.control.medico.controlmedico.controller;

import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.Movimiento;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import com.control.medico.controlmedico.service.ControlMedicoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Set;

@Controller
public class ControlMedicoController {

    private static final Logger log = LoggerFactory.getLogger(ControlMedicoController.class);
    private final ControlMedicoService controlMedicoService;
    private final FamiliarRepository familiarRepository;

    public ControlMedicoController(ControlMedicoService controlMedicoService, FamiliarRepository familiarRepository) {
        this.controlMedicoService = controlMedicoService;
        this.familiarRepository = familiarRepository;
    }

    @GetMapping("/")
    public String dashboard(Authentication authentication, @RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        log.info("[LOG-DASHBOARD] ──> Petición recibida en ruta raíz (/)");

        if (authentication == null) {
            log.warn("[LOG-DASHBOARD] ⚠️ La autenticación es NULL. El usuario no está firmado.");
            return "redirect:/login";
        }

        String usernameLogueado = authentication.getName();
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        
        log.info("[LOG-DASHBOARD] 👤 Usuario firmado: '{}'", usernameLogueado);
        log.info("[LOG-DASHBOARD] 🔑 Roles detectados en el token de sesión: {}", roles);

        // 1. Desvío inmediato si es SYS_ADMIN
        if (roles.contains("ROLE_SYS_ADMIN")) {
            log.info("[LOG-DASHBOARD] 🔀 Detectado ROLE_SYS_ADMIN. Redireccionando a /sys-admin/dashboard");
            return "redirect:/sys-admin/dashboard";
        }

        // 2. Localizar datos del inquilino
        log.info("[LOG-DASHBOARD] 🔍 Buscando datos del familiar para vincular Multi-Tenant...");
        Familiar familiar = familiarRepository.findByUsername(usernameLogueado)
                .orElseThrow(() -> {
                    log.error("[LOG-DASHBOARD] ❌ ERROR CRÍTICO: El usuario logueado '{}' no se encuentra en la tabla familiar.", usernameLogueado);
                    return new RuntimeException("Usuario no encontrado en la sesión");
                });

        if (familiar.getFamilia() == null) {
            log.error("[LOG-DASHBOARD] ❌ ERROR CRÍTICO: El familiar '{}' no tiene asignada ninguna Familia en la BD.", usernameLogueado);
            throw new RuntimeException("El usuario no pertenece a ninguna familia.");
        }

        Long familiaId = familiar.getFamilia().getId();
        log.info("[LOG-DASHBOARD] 🏠 Multi-Tenant Activo. Familia ID vinculada: {}", familiaId);

        // 3. Cálculos Financieros
        log.info("[LOG-DASHBOARD] 📊 Ejecutando sumatorias financieras por inquilino...");
        BigDecimal totalAportado = controlMedicoService.calcularTotalAportado(familiaId);
        BigDecimal totalGastado = controlMedicoService.calcularTotalGastado(familiaId);
        BigDecimal balanceDisponible = controlMedicoService.calcularBalanceDisponible(familiaId);
        BigDecimal cuotaFamiliar = controlMedicoService.calcularCuotaFamiliar(familiaId);

        log.info("[LOG-DASHBOARD] 💰 Totales calculados -> Aportado: {}, Gastado: {}, Balance: {}", 
                 totalAportado, totalGastado, balanceDisponible);

        // 4. Paginación
        log.info("[LOG-DASHBOARD] 📄 Solicitando movimientos de la familia. Página: {}", page);
        Page<Movimiento> paginaMovimientos = controlMedicoService.obtenerMovimientosPorFamiliaPaginado(familiaId, page);
        log.info("[LOG-DASHBOARD] 📦 Movimientos recuperados: {}. Total páginas: {}", 
                 paginaMovimientos.getNumberOfElements(), paginaMovimientos.getTotalPages());

        // Inyección al modelo Thymeleaf
        model.addAttribute("totalAportado", totalAportado);
        model.addAttribute("totalGastado", totalGastado);
        model.addAttribute("balanceDisponible", balanceDisponible);
        model.addAttribute("cuotaFamiliar", cuotaFamiliar);
        model.addAttribute("movimientos", paginaMovimientos.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("familias", paginaMovimientos); 
        model.addAttribute("familiar", familiar);
        model.addAttribute("nuevoMovimiento", new Movimiento());

        log.info("[LOG-DASHBOARD] ✔️ Todo correcto. Renderizando vista 'dashboard.html'");
        return "dashboard";
    }
}