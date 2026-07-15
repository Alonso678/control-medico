package com.control.medico.controlmedico.controller;

import com.control.medico.controlmedico.model.Categoria;
import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.Movimiento;
import com.control.medico.controlmedico.model.TipoMembresia;
import com.control.medico.controlmedico.repository.CategoriaRepository;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import com.control.medico.controlmedico.repository.MovimientoRepository;
import com.control.medico.controlmedico.service.ControlMedicoService;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.lowagie.text.*;

import java.io.ByteArrayOutputStream;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.List;

@Controller
public class ControlMedicoController {

    private static final Logger log = LoggerFactory.getLogger(ControlMedicoController.class);
    private final ControlMedicoService controlMedicoService;
    private final FamiliarRepository familiarRepository;
    // 1. Inyecta el nuevo repositorio en el constructor del controlador
    private final CategoriaRepository categoriaRepository;

    private final MovimientoRepository movimientoRepository;

    public ControlMedicoController(ControlMedicoService controlMedicoService,
            FamiliarRepository familiarRepository,
            CategoriaRepository categoriaRepository,
            MovimientoRepository movimientoRepository) {
        this.controlMedicoService = controlMedicoService;
        this.familiarRepository = familiarRepository;
        this.categoriaRepository = categoriaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @GetMapping("/")
    public String dashboard(Authentication authentication, @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {
        log.info("[LOG-DASHBOARD] ──> Petición recibida en ruta raíz (/)");

        if (authentication == null) {
            log.warn("[LOG-DASHBOARD] La autenticación es NULL. Redireccionando a login.");
            return "redirect:/login";
        }

        String usernameLogueado = authentication.getName();
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());

        log.info("[LOG-DASHBOARD] 👤 Usuario firmado: '{}'", usernameLogueado);
        log.info("[LOG-DASHBOARD] 🔑 Roles detectados: {}", roles);

        // 1. DESVÍO DE SEGURIDAD: SYS_ADMIN va a su consola global
        if (roles.contains("ROLE_SYS_ADMIN")) {
            log.info("[LOG-DASHBOARD] 🔀 Detectado ROLE_SYS_ADMIN. Redireccionando a consola global.");
            return "redirect:dashboard";
        }

        // 2. MULTI-TENANCY: Localizar datos de la familia asignada al usuario
        Familiar familiar = familiarRepository.findByUsername(usernameLogueado)
                .orElseThrow(() -> {
                    log.error("[LOG-DASHBOARD] ❌ ERROR: El usuario '{}' no existe en familiares.", usernameLogueado);
                    return new RuntimeException("Usuario no encontrado en la sesión");
                });

        if (familiar.getFamilia() == null) {
            log.error("[LOG-DASHBOARD] ❌ El familiar '{}' no pertenece a ninguna familia.", usernameLogueado);
            throw new RuntimeException("El usuario no tiene una familia asignada.");
        }

        Long familiaId = familiar.getFamilia().getId();
        log.info("[LOG-DASHBOARD] 🏠 Multi-Tenant Activo. Familia ID vinculada: {}", familiaId);

        // 3. CÁLCULOS FINANCIEROS (Aislados por familiaId)
        BigDecimal totalAportado = controlMedicoService.calcularTotalAportado(familiaId);
        BigDecimal totalGastado = controlMedicoService.calcularTotalGastado(familiaId);
        BigDecimal balanceDisponible = controlMedicoService.calcularBalanceDisponible(familiaId);
        BigDecimal cuotaFamiliar = controlMedicoService.calcularCuotaFamiliar(familiaId);

        // 4. MOVIMIENTOS PAGINADOS
        Page<Movimiento> paginaMovimientos = controlMedicoService.obtenerMovimientosPorFamiliaPaginado(familiaId, page);

        // 5. INYECCIÓN DE DATOS COMUNES AL MODELO
        model.addAttribute("totalAportado", totalAportado);
        model.addAttribute("totalGastado", totalGastado);
        model.addAttribute("balanceDisponible", balanceDisponible);
        model.addAttribute("cuotaFamiliar", cuotaFamiliar);
        model.addAttribute("movimientos", paginaMovimientos.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", paginaMovimientos.getTotalPages());
        model.addAttribute("familiar", familiar);
        model.addAttribute("familiares", familiarRepository.findByFamiliaId(familiaId));
        model.addAttribute("categorias", categoriaRepository.findAll());

        // ✔️ Inicialización correcta y única del objeto para el formulario
        Movimiento nuevoMovimiento = new Movimiento();
        nuevoMovimiento.setFamiliar(new Familiar());
        nuevoMovimiento.setCategoria(new Categoria());
        model.addAttribute("nuevoMovimiento", nuevoMovimiento);

        // 6. CONTROL DE ACCESO POR ROLES EN LA VISTA
        if (roles.contains("ROLE_ADMIN")) {
            log.info(
                    "[LOG-DASHBOARD] 🛡️ Usuario es ADMINISTRADOR de la familia. Renderizando panel de control total.");
            // ❌ ELIMINADA la línea: model.addAttribute("nuevoMovimiento", new
            // Movimiento());
            return "dashboard-admin";
        } else if (roles.contains("ROLE_FAMILIAR")) {
            log.info("[LOG-DASHBOARD] 👥 Usuario es FAMILIAR estándar. Renderizando panel de solo lectura.");
            return "dashboard-familiar";
        }

        log.warn("[LOG-DASHBOARD] Usuario sin rol válido.");
        return "redirect:/login";
    }

    // --- ENDPOINT 1: PANTALLA DE SELECCIÓN DE FAMILIARES ---
    @GetMapping("/reporte")
    public String irAPantallaReporteIndividual(Authentication authentication, Model model) {
        log.info("[LOG-REPORTE] ──> Cargando pantalla de selección de aportadores");
        if (authentication == null)
            return "redirect:/login";

        String usernameLogueado = authentication.getName();
        Familiar familiar = familiarRepository.findByUsername(usernameLogueado)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Filtrar miembros APORTADORES de su misma familia
        List<Familiar> aportadores = familiarRepository.findByFamiliaAndTipoMembresia(
                familiar.getFamilia(),
                TipoMembresia.APORTADOR);

        model.addAttribute("miembros", aportadores);
        model.addAttribute("familiar", familiar);

        return "reporte-individual";
    }

    // --- ENDPOINT 2: DESCARGA DE PDF INDIVIDUAL ---
    @GetMapping("/reporte/descargar/{id}")
    public ResponseEntity<byte[]> descargarReporteMiembroSeleccionado(@PathVariable("id") Long familiarId,
            Authentication authentication) {
        log.info("[LOG-REPORTE] ──> Solicitud de descarga de PDF para familiar ID: {}", familiarId);
        if (authentication == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            Familiar familiarDestino = familiarRepository.findById(familiarId)
                    .orElseThrow(() -> new RuntimeException("Miembro de la familia no encontrado"));

            Long familiaId = familiarDestino.getFamilia().getId();

            // 1. Obtener datos financieros necesarios para el formato
            BigDecimal totalGastosGrupo = controlMedicoService.calcularTotalGastado(familiaId);

            long totalMiembros = familiarRepository.countByFamiliaAndTipoMembresia(
                    familiarDestino.getFamilia(),
                    TipoMembresia.APORTADOR);
            if (totalMiembros == 0)
                totalMiembros = 1;

            List<Movimiento> aportaciones = movimientoRepository.findByFamiliarIdAndTipo(familiarId, "APORTACION");

            // 2. Invocar al método optimizado para generar el PDF
            byte[] pdfBytes = generarPdf(familiarDestino, totalGastosGrupo, totalMiembros, aportaciones);

            // 3. Responder con el archivo para su descarga
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename("Reporte_" + familiarDestino.getNombre().replace(" ", "_") + "_"
                            + java.time.LocalDate.now() + ".pdf")
                    .build());

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("[LOG-REPORTE] ❌ Error al procesar la descarga del reporte: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // =========================================================================
    // MÉTODO OPTIMIZADO Y REUTILIZABLE PARA LA GENERACIÓN DEL PDF
    // =========================================================================
    private byte[] generarPdf(Familiar familiar, BigDecimal totalGastosGrupo, long totalMiembros,
            List<Movimiento> aportaciones) throws Exception {

        // --- 1. Cálculos de Negocio Internos ---
        BigDecimal cuotaIndividual = totalGastosGrupo.divide(BigDecimal.valueOf(totalMiembros), 2,
                RoundingMode.HALF_UP);

        BigDecimal totalAportadoFamiliar = BigDecimal.ZERO;
        if (aportaciones != null) {
            for (Movimiento mov : aportaciones) {
                if (mov != null && mov.getMonto() != null) {
                    totalAportadoFamiliar = totalAportadoFamiliar.add(mov.getMonto());
                }
            }
        }

        BigDecimal cantidadFaltante = cuotaIndividual.subtract(totalAportadoFamiliar);

        // --- 2. Inicialización del Documento (OpenPDF) ---
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36); // Márgenes de 0.5 in (36pt)
        PdfWriter.getInstance(document, out);

        document.open();

        // --- 3. Estilos y Fuentes ---
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD);
        Font fontSubtitulo = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL);
        Font fontSeccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD);
        Font fontTablaHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD);
        Font fontTablaData = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL);

        // --- 4. Encabezados de la Página ---
        Paragraph titulo = new Paragraph("REPORTE INDIVIDUAL - CONTROL MÉDICO", fontTitulo);
        titulo.setAlignment(Element.ALIGN_LEFT);
        document.add(titulo);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Paragraph fechaEmision = new Paragraph("Fecha de emisión : " + java.time.LocalDate.now().format(formatter),
                fontSubtitulo);
        document.add(fechaEmision);

        Paragraph solicitadoPor = new Paragraph("Reporte Solicitado por: " + familiar.getNombre(), fontSubtitulo);
        document.add(solicitadoPor);

        document.add(new Paragraph(" ")); // Espacio

        // --- 5. TABLA 1: Resumen de Conceptos Globales ---
        PdfPTable tablaConceptos = new PdfPTable(2);
        tablaConceptos.setWidthPercentage(100);
        tablaConceptos.setWidths(new float[] { 3.5f, 1.5f });

        // Headers Tabla 1
        tablaConceptos.addCell(crearCeldaHeader("Concepto Global", fontTablaHeader));
        tablaConceptos.addCell(crearCeldaHeader("Monto ($)", fontTablaHeader));

        // Datos Tabla 1
        tablaConceptos.addCell(crearCeldaComun("Total de Gastos Médicos del Grupo:", fontTablaData));
        tablaConceptos.addCell(crearCeldaComun("$" + totalGastosGrupo, fontTablaData));

        tablaConceptos.addCell(
                crearCeldaComun("Cantidad que le corresponde aportar (Total / " + totalMiembros + "):", fontTablaData));
        tablaConceptos.addCell(crearCeldaComun("$" + cuotaIndividual, fontTablaData));

        tablaConceptos.addCell(crearCeldaComun("Total que has aportado a la fecha:", fontTablaData));
        tablaConceptos.addCell(crearCeldaComun("$" + totalAportadoFamiliar, fontTablaData));

        tablaConceptos.addCell(crearCeldaComun("CANTIDAD FALTANTE POR COMPLETAR:", fontTablaData));
        tablaConceptos.addCell(crearCeldaComun("$" + cantidadFaltante, fontTablaData));

        document.add(tablaConceptos);

        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        // --- 6. TABLA 2: Historial de Aportaciones ---
        Paragraph seccionAportaciones = new Paragraph("HISTORIAL DE APORTACIONES RECAUDADAS", fontSeccion);
        document.add(seccionAportaciones);
        document.add(new Paragraph(" "));

        PdfPTable tablaAportaciones = new PdfPTable(3);
        tablaAportaciones.setWidthPercentage(100);
        tablaAportaciones.setWidths(new float[] { 1.5f, 3.5f, 1.5f });

        // Headers Tabla 2
        tablaAportaciones.addCell(crearCeldaHeader("Fecha", fontTablaHeader));
        tablaAportaciones.addCell(crearCeldaHeader("Descripción", fontTablaHeader));
        tablaAportaciones.addCell(crearCeldaHeader("Monto Aportado", fontTablaHeader));

        // Datos Tabla 2
        if (aportaciones == null || aportaciones.isEmpty()) {
            PdfPCell celdaVacia = new PdfPCell(
                    new Paragraph("No se registran aportaciones para este miembro a la fecha.", fontTablaData));
            celdaVacia.setColspan(3);
            celdaVacia.setPadding(8);
            celdaVacia.setHorizontalAlignment(Element.ALIGN_CENTER);
            tablaAportaciones.addCell(celdaVacia);
        } else {
            for (Movimiento mov : aportaciones) {
                if (mov != null) {
                    String fechaStr = mov.getFecha() != null ? mov.getFecha().toString() : "";
                    String descStr = mov.getDescripcion() != null ? mov.getDescripcion() : "";
                    String montoStr = mov.getMonto() != null ? "$" + mov.getMonto() : "$0.00";

                    tablaAportaciones.addCell(crearCeldaComun(fechaStr, fontTablaData));
                    tablaAportaciones.addCell(crearCeldaComun(descStr, fontTablaData));
                    tablaAportaciones.addCell(crearCeldaComun(montoStr, fontTablaData));
                }
            }
        }

        document.add(tablaAportaciones);
        document.close();

        return out.toByteArray();
    }

    // Métodos de ayuda para formatear las celdas rápidamente
    private PdfPCell crearCeldaHeader(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(texto, font));
        cell.setPadding(6);
        cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY); // Opcional: fondo gris claro para los encabezados de tabla
        return cell;
    }

    private PdfPCell crearCeldaComun(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(texto, font));
        cell.setPadding(6);
        return cell;
    }

    @PostMapping("/movimientos/guardar")
    public String guardarMovimiento(@ModelAttribute("nuevoMovimiento") Movimiento movimiento,
            BindingResult result,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        log.info("[LOG-CATEGORIA-DIAGNOSTICO] 🔍 1. Entrada al endpoint guardarMovimiento.");

        // --- LOGS DE BINDING INICIAL ---
        if (movimiento != null) {
            log.info("[LOG-CATEGORIA-DIAGNOSTICO] Tipo recibido: '{}'", movimiento.getTipo());
            log.info("[LOG-CATEGORIA-DIAGNOSTICO] Categoria: '{}'", movimiento.getCategoria());
            log.info("[LOG-CATEGORIA-DIAGNOSTICO] Monto recibido: '{}'", movimiento.getMonto());
            log.info("[LOG-CATEGORIA-DIAGNOSTICO] Descripción recibida: '{}'", movimiento.getDescripcion());

            if (movimiento.getCategoria() != null) {
                log.info("[LOG-CATEGORIA-DIAGNOSTICO] Objeto Categoria NO es null. ID de categoría en binding: {}",
                        movimiento.getCategoria().getId());
            } else {
                log.warn("[LOG-CATEGORIA-DIAGNOSTICO] ⚠️ El objeto Categoria recibido en el @ModelAttribute es NULL.");
            }
        } else {
            log.error("[LOG-CATEGORIA-DIAGNOSTICO] ❌ El objeto Movimiento recibido es completamente NULL.");
        }

        // 1. Validar errores de binding de Spring
        if (result.hasErrors()) {
            log.error("[LOG-MOVIMIENTO] ❌ Error de binding en el formulario: {}", result.getAllErrors());
            redirectAttributes.addFlashAttribute("mensajeError", "Datos del formulario inválidos.");
            return "redirect:/";
        }

        try {
            if (authentication == null) {
                log.warn("[LOG-CATEGORIA-DIAGNOSTICO] Autenticación nula. Redirigiendo a login.");
                return "redirect:/login";
            }

            String username = authentication.getName();
            Familiar administradorLogueado = familiarRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Administrador no encontrado"));

            // REFUERZO DE SEGURIDAD: Solo el administrador de la familia puede guardar
            // movimientos
            if (Boolean.FALSE.equals(administradorLogueado.getAdministrador())) {
                log.warn("[LOG-CATEGORIA-DIAGNOSTICO] ⚠️ Acceso no autorizado para: {}", username);
                return "redirect:/access-denied";
            }

            // 1. Verificación de seguridad inicial (Evita el NullPointerException de raíz)
            if (movimiento == null) {
                log.error("[LOG-MOVIMIENTO] ❌ El objeto movimiento recibido en el controlador es NULL.");
                redirectAttributes.addFlashAttribute("mensajeError", "El movimiento no pudo ser procesado.");
                return "redirect:/";
            }

            // 2. Obtener el familiar seleccionado en el modal del formulario de forma
            // segura
            if (movimiento.getFamiliar() == null || movimiento.getFamiliar().getId() == null) {
                log.warn("[LOG-MOVIMIENTO] ⚠️ Intento de guardar sin especificar un familiar.");
                throw new RuntimeException("Debe seleccionar un familiar válido.");
            }
            Familiar familiarSeleccionado = familiarRepository.findById(movimiento.getFamiliar().getId())
                    .orElseThrow(() -> new RuntimeException("El familiar seleccionado no existe."));

            // 3. Vincular familiar y familia del administrador
            movimiento.setFamiliar(familiarSeleccionado);
            movimiento.setFamilia(administradorLogueado.getFamilia());

            if (movimiento.getFecha() == null) {
                movimiento.setFecha(java.time.LocalDate.now());
            }

            // 4. PERSISTENCIA DE CATEGORÍA CON LOGS DETALLADOS
            log.info("[LOG-CATEGORIA-DIAGNOSTICO] ⚡ Evaluando condicional de Tipo de Movimiento: '{}'",
                    movimiento.getTipo());

            if ("APORTACION".equals(movimiento.getTipo())) {
                log.info("[LOG-CATEGORIA-DIAGNOSTICO] Tipo es APORTACION. Forzando categoría a NULL.");
                movimiento.setCategoria(null);
            } else if ("GASTO".equals(movimiento.getTipo())) {
                log.info("[LOG-CATEGORIA-DIAGNOSTICO] Tipo es GASTO. Procesando categoría...");

                if (movimiento.getCategoria() != null && movimiento.getCategoria().getId() != null) {
                    Long catId = movimiento.getCategoria().getId();
                    log.info(
                            "[LOG-CATEGORIA-DIAGNOSTICO] Intentando buscar Categoría con ID: {} en la Base de Datos...",
                            catId);

                    Categoria categoriaPersistida = categoriaRepository.findById(catId)
                            .orElse(null);

                    if (categoriaPersistida != null) {
                        log.info("[LOG-CATEGORIA-DIAGNOSTICO] ¡Categoría encontrada con éxito! Nombre: '{}'",
                                categoriaPersistida.getNombre());
                        movimiento.setCategoria(categoriaPersistida);
                    } else {
                        log.error("[LOG-CATEGORIA-DIAGNOSTICO] ❌ La categoría con ID {} NO EXISTE en la base de datos.",
                                catId);
                        movimiento.setCategoria(null);
                    }
                } else {
                    log.warn(
                            "[LOG-CATEGORIA-DIAGNOSTICO] ⚠️ Se envió un GASTO pero el ID de categoría llegó vacío/nulo.");
                    movimiento.setCategoria(null);
                }
            }

            // Log de estado final previo a guardar
            log.info(
                    "[LOG-MOVIMIENTO] 💾 Guardando en DB -> Tipo: '{}', Familiar: '{}', Categoria asignada final: '{}', Monto: ${}",
                    movimiento.getTipo(),
                    movimiento.getFamiliar() != null ? movimiento.getFamiliar().getNombre() : "NULO",
                    movimiento.getCategoria() != null
                            ? movimiento.getCategoria().getNombre() + " (ID: " + movimiento.getCategoria().getId() + ")"
                            : "NINGUNA (NULL)",
                    movimiento.getMonto());

            // 5. Guardar en la Base de Datos
            Movimiento guardado = movimientoRepository.save(movimiento);
            log.info(
                    "[LOG-CATEGORIA-DIAGNOSTICO] ✅ ¡Persistencia completada! ID asignado por DB: {}. Categoria en objeto guardado: '{}'",
                    guardado.getId(),
                    guardado.getCategoria() != null ? guardado.getCategoria().getNombre() : "NULL");

            redirectAttributes.addFlashAttribute("mensajeExito", "Movimiento guardado con éxito.");

        } catch (Exception e) {
            log.error("[LOG-MOVIMIENTO] ❌ Error fatal al guardar el movimiento: ", e);
            redirectAttributes.addFlashAttribute("mensajeError", "No se pudo guardar el movimiento: " + e.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/movimientos/eliminar")
    public String eliminarMovimiento(@RequestParam("id") Long id) {
        controlMedicoService.eliminarMovimiento(id);
        return "redirect:/"; // Redirecciona al Dashboard para ver los cambios
    }
}