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
import java.util.List;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import jakarta.servlet.http.HttpServletResponse;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
     * Modal de Reportes Individuales
     */
    @GetMapping("/reporte")
    public void generarReportePDF(@RequestParam("familiarId") Long familiarId, HttpServletResponse response)
            throws Exception {
        // 1. Obtener datos clave de la base de datos
        var familiarOpt = controlMedicoService.obtenerFamiliarPorId(familiarId); // Asegúrate de tener este método en tu
                                                                                 // service o búscalo por repo
        if (familiarOpt.isEmpty()) {
            response.sendRedirect("/?error=FamiliarNoEncontrado");
            return;
        }
        var familiar = familiarOpt.get();

        // 2. Cálculos Financieros del Negocio
        Map<String, BigDecimal> resumenGlobal = controlMedicoService.obtenerResumenFinancieroGlobal();
        BigDecimal totalGastosGlobal = resumenGlobal.get("totalGastado");

        // Cuota correspondiente: Total Gastos / 4
        /*
         * BigDecimal cuotaCorrespondiente = totalGastosGlobal.divide(new
         * BigDecimal("4"), 2,
         * java.math.RoundingMode.HALF_UP);
         */
        // 1. Obtener cuántos miembros cooperan en ESTA familia en específico
        long numeroAportadores = controlMedicoService.contarAportadoresPorFamilia(familiar.getFamilia().getId());
        // 2. Si la familia tiene aportadores, dividimos el gasto entre el número real de ellos
        BigDecimal cuotaCorrespondiente = BigDecimal.ZERO;
        if (numeroAportadores > 0) {
            cuotaCorrespondiente = totalGastosGlobal.divide(
                    new BigDecimal(numeroAportadores), 2, java.math.RoundingMode.HALF_UP);
        }

        // Aportaciones hechas por ESTE familiar individual
        Map<String, BigDecimal> resumenFamiliar = controlMedicoService.obtenerResumenFinancieroPorFamiliar(familiarId);
        BigDecimal totalAportadoPorFamiliar = resumenFamiliar.get("aportado");

        // Calcular si tiene saldo faltante
        BigDecimal faltante = cuotaCorrespondiente.subtract(totalAportadoPorFamiliar);
        if (faltante.compareTo(BigDecimal.ZERO) < 0) {
            faltante = BigDecimal.ZERO; // Si aportó de más, el faltante es 0
        }

        List<Movimiento> aportacionesFamiliar = controlMedicoService.obtenerMovimientosPorFamiliar(familiarId)
                .stream()
                .filter(m -> m.getTipo().toString().equals("APORTACION"))
                .toList();

        // 3. Configurar Cabeceras de Respuesta para Descarga de Archivo
        response.setContentType("application/pdf");
        String fechaHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String cleanNombre = familiar.getNombre().replaceAll("\\s+", "_");
        response.setHeader("Content-Disposition",
                "attachment; filename=Reporte_" + cleanNombre + "_" + fechaHoy + ".pdf");

        // 4. Construcción del documento PDF usando OpenPDF
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);

        document.open();

        // Fuentes estilizadas
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
        Font fontSub = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
        Font fontSeccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

        // Encabezado
        Paragraph titulo = new Paragraph("REPORTE INDIVIDUAL - CONTROL MÉDICO", fontTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        document.add(titulo);

        Paragraph subtitulo = new Paragraph(
                "Fecha de emisión (HOY): " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                fontSub);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(20);
        document.add(subtitulo);

        // Información del Familiar
        document.add(new Paragraph("Reporte Solicitado por: " + familiar.getNombre(), fontSeccion));
        document.add(new Paragraph("__________________________________________________________________\n\n", fontSub));

        // Tabla de Resumen Financiero Matemático
        PdfPTable tablaResumen = new PdfPTable(2);
        tablaResumen.setWidthPercentage(100);
        tablaResumen.setSpacingAfter(20);

        tablaResumen.addCell(new PdfPCell(new Phrase("Concepto Global", fontBold)));
        tablaResumen.addCell(new PdfPCell(new Phrase("Monto ($)", fontBold)));

        tablaResumen.addCell(new PdfPCell(new Phrase("Total de Gastos Médicos del Grupo:", fontNormal)));
        tablaResumen.addCell(new PdfPCell(new Phrase("$" + totalGastosGlobal.toString(), fontNormal)));

        tablaResumen.addCell(new PdfPCell(new Phrase("Cantidad que le corresponde aportar (Total / 4):", fontBold)));
        tablaResumen.addCell(new PdfPCell(new Phrase("$" + cuotaCorrespondiente.toString(), fontBold)));

        tablaResumen.addCell(new PdfPCell(new Phrase("Total que has aportado a la fecha:", fontNormal)));
        tablaResumen.addCell(new PdfPCell(new Phrase("$" + totalAportadoPorFamiliar.toString(), fontNormal)));

        PdfPCell celdaFaltanteTxt = new PdfPCell(new Phrase("CANTIDAD FALTANTE POR COMPLETAR:", fontBold));
        celdaFaltanteTxt.setBackgroundColor(new Color(255, 235, 235));
        PdfPCell celdaFaltanteNum = new PdfPCell(new Phrase("$" + faltante.toString(), fontBold));
        celdaFaltanteNum.setBackgroundColor(new Color(255, 235, 235));

        tablaResumen.addCell(celdaFaltanteTxt);
        tablaResumen.addCell(celdaFaltanteNum);

        document.add(tablaResumen);

        // Desglose de Aportaciones Realizadas
        document.add(new Paragraph("HISTORIAL DE APORTACIONES RECAUDADAS", fontSeccion));
        document.add(new Paragraph(" ", fontNormal));

        PdfPTable tablaAportaciones = new PdfPTable(3);
        tablaAportaciones.setWidthPercentage(100);

        tablaAportaciones.addCell(new PdfPCell(new Phrase("Fecha", fontBold)));
        tablaAportaciones.addCell(new PdfPCell(new Phrase("Descripción", fontBold)));
        tablaAportaciones.addCell(new PdfPCell(new Phrase("Monto Aportado", fontBold)));

        if (aportacionesFamiliar.isEmpty()) {
            PdfPCell vacio = new PdfPCell(
                    new Phrase("No se registran aportaciones de fondos asignadas a este familiar.", fontNormal));
            vacio.setColspan(3);
            vacio.setHorizontalAlignment(Element.ALIGN_CENTER);
            tablaAportaciones.addCell(vacio);
        } else {
            for (Movimiento mov : aportacionesFamiliar) {
                tablaAportaciones.addCell(new PdfPCell(new Phrase(mov.getFecha().toString(), fontNormal)));
                tablaAportaciones.addCell(new PdfPCell(new Phrase(mov.getDescripcion()))); 
                tablaAportaciones.addCell(new PdfPCell(new Phrase("$" + mov.getMonto().toString(), fontNormal)));
            }
        }

        document.add(tablaAportaciones);
        document.close();

        // Enviar el stream al canal de respuesta de red
        response.getOutputStream().write(baos.toByteArray());
        response.getOutputStream().flush();
    }

    @PostMapping("/movimientos/eliminar")
    public String eliminarMovimiento(@RequestParam("id") Long id) {
        controlMedicoService.eliminarMovimiento(id);
        return "redirect:/"; // Redirecciona al Dashboard para ver los cambios
    }
}