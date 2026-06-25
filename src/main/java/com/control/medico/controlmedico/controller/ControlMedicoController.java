package com.control.medico.controlmedico.controller;

import com.control.medico.controlmedico.model.Movimiento;
import com.control.medico.controlmedico.service.ControlMedicoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.Map;

@Controller
public class ControlMedicoController {

    private final ControlMedicoService controlMedicoService;

    // Inyección limpia por constructor (Cero Warnings)
    public ControlMedicoController(ControlMedicoService controlMedicoService) {
        this.controlMedicoService = controlMedicoService;
    }

    /**
     * Dashboard Principal: Muestra balances generales, historial completo 
     * y carga los objetos necesarios para el modal de registro.
     */
    @GetMapping("/")
    public String dashboard(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        // 1. Obtener sumatorias globales (Estas no se paginan, se quedan igual)
        Map<String, BigDecimal> resumen = controlMedicoService.obtenerResumenFinancieroGlobal();
        model.addAttribute("totalAportado", resumen.get("totalAportado"));
        model.addAttribute("totalGastado", resumen.get("totalGastado"));
        model.addAttribute("balanceDisponible", resumen.get("balanceDisponible"));

        // 2. Obtener la página de movimientos solicitada (de 20 en 20)
        Page<Movimiento> paginaMovimientos = controlMedicoService.obtenerTodosLosMovimientos(page);

        // Pasamos el contenido de la página y los datos de control de paginación a la
        // vista
        model.addAttribute("movimientos", paginaMovimientos.getContent());
        model.addAttribute("paginaActual", page);
        model.addAttribute("totalPaginas", paginaMovimientos.getTotalPages());

        // 3. Objetos necesarios para el Modal de Registro
        model.addAttribute("movimiento", new Movimiento());
        model.addAttribute("familiares", controlMedicoService.obtenerTodosLosFamiliares());
        model.addAttribute("categorias", controlMedicoService.obtenerTodasLasCategorias());

        return "dashboard";
    }

    /**
     * Procesa el envío del formulario del nuevo movimiento (vía Modal o formulario
     * externo)
     */
    @PostMapping("/movimientos/guardar")
    public String guardarMovimiento(@ModelAttribute("movimiento") Movimiento movimiento) {
        controlMedicoService.registrarMovimiento(movimiento);
        return "redirect:/"; // Redirecciona de vuelta al tablero principal refrescando los datos
    }

    /**
     * Pantalla de Reportes Individuales
     */
    @GetMapping("/reporte")
    public String reporteIndividual(@RequestParam(value = "familiarId", required = false) Long familiarId, Model model) {
        model.addAttribute("familiares", controlMedicoService.obtenerTodosLosFamiliares());
        
        if (familiarId != null) {
            Map<String, BigDecimal> resumenFamiliar = controlMedicoService.obtenerResumenFinancieroPorFamiliar(familiarId);
            model.addAttribute("aportado", resumenFamiliar.get("aportado"));
            model.addAttribute("gastado", resumenFamiliar.get("gastado"));
            model.addAttribute("balance", resumenFamiliar.get("balance"));
            model.addAttribute("movimientosFamiliar", controlMedicoService.obtenerMovimientosPorFamiliar(familiarId));
            model.addAttribute("familiarSeleccionado", familiarId);
        }
        
        return "reporte-individual"; // Apunta a reporte-individual.html
    }

    @PostMapping("/movimientos/eliminar")
    public String eliminarMovimiento(@RequestParam("id") Long id) {
        controlMedicoService.eliminarMovimiento(id);
        return "redirect:/"; // Redirecciona al Dashboard para ver los cambios
    }
}