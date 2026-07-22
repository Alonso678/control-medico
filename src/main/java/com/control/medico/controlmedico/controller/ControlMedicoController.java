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

        log.info("[LOG-DASHBOARD] Usuario firmado: '{}'", usernameLogueado);
        log.info("[LOG-DASHBOARD] Roles detectados: {}", roles);

        // 1. DESVÍO DE SEGURIDAD: SYS_ADMIN va a su consola global
        if (roles.contains("ROLE_SYS_ADMIN")) {
            log.info("[LOG-DASHBOARD] Detectado ROLE_SYS_ADMIN. Redireccionando a consola global.");
            return "redirect:dashboard";
        }

        // 2. MULTI-TENANCY: Localizar datos de la familia asignada al usuario
        Familiar familiar = familiarRepository.findByUsername(usernameLogueado)
                .orElseThrow(() -> {
                    log.error("[LOG-DASHBOARD] ERROR: El usuario '{}' no existe en familiares.", usernameLogueado);
                    return new RuntimeException("Usuario no encontrado en la sesión");
                });

        if (familiar.getFamilia() == null) {
            log.error("[LOG-DASHBOARD] El familiar '{}' no pertenece a ninguna familia.", usernameLogueado);
            throw new RuntimeException("El usuario no tiene una familia asignada.");
        }

        Long familiaId = familiar.getFamilia().getId();
        log.info("[LOG-DASHBOARD] Multi-Tenant Activo. Familia ID vinculada: {}", familiaId);

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

        model.addAttribute("esSysAdmin", false);

        // 6. CONTROL DE ACCESO POR ROLES EN LA VISTA
        if (roles.contains("ROLE_ADMIN")) {
            log.info(
                    "[LOG-DASHBOARD] Usuario es ADMINISTRADOR de la familia. Renderizando panel de control total.");
            // ELIMINADA la línea: model.addAttribute("nuevoMovimiento", new
            // Movimiento());
            return "dashboard-admin";
        } else if (roles.contains("ROLE_FAMILIAR")) {
            log.info("[LOG-DASHBOARD] Usuario es FAMILIAR estándar. Renderizando panel de solo lectura.");
            return "dashboard-admin";
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
            log.error("[LOG-REPORTE] Error al procesar la descarga del reporte: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // =========================================================================
    // MÉTODO MEJORADO CON DISEÑO EDITORIAL PROFESIONAL (OPENPDF)
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
        boolean tieneDeuda = cantidadFaltante.compareTo(BigDecimal.ZERO) > 0;
        java.text.DecimalFormat df = new java.text.DecimalFormat("$#,##0.00");

        // --- 2. Inicialización del Documento ---
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Cambiamos el margen inferior de 45 a 55 para proteger la zona del Footer
        Document document = new Document(PageSize.A4, 45, 45, 45, 55);
        PdfWriter writer = PdfWriter.getInstance(document, out);

        // REGISTRO DEL MANEJADOR VISUAL (Logo y Marca de Agua)
        DiseñoPdfHelper helper = new DiseñoPdfHelper();
        writer.setPageEvent(helper);

        document.open();

        // --- 3. Paleta de Colores Corporativos (Actualizados a tu paleta) ---
        java.awt.Color azulPrimario = new java.awt.Color(13, 110, 253); // #0d6efd (Primary)
        java.awt.Color azulAcento = new java.awt.Color(13, 202, 240); // #0dcaf0 (Accent)
        java.awt.Color azulClaro = new java.awt.Color(235, 243, 255); // Fondo suave para headers de tablas
        java.awt.Color verdeExito = new java.awt.Color(47, 133, 90); // Para montos a favor o correctos
        java.awt.Color rojoAlerta = new java.awt.Color(197, 48, 48); // Para deudas
        java.awt.Color grisTexto = new java.awt.Color(74, 85, 104); // Textos secundarios // #4a5568

        // --- 4. Fuentes Profesionales ---
        Font fontHeaderTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, Font.BOLD, java.awt.Color.WHITE);
        Font fontHeaderSub = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, azulAcento); 
        Font fontHeaderMeta = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL,
                new java.awt.Color(240, 244, 248));

        Font fontSeccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, azulPrimario);
        Font fontTablaHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, grisTexto);
        Font fontTablaData = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, azulPrimario);
        Font fontTablaDataGris = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, grisTexto);
        //Font fontMontoDestacado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD);

        // --- 5. BANNER CORPORATIVO DE ENCABEZADO (Diseño de Tarjeta Blanca para el
        // Logo) ---
        // Estructura de 2 columnas principales: [Contenedor del Logo (3.8)] [Textos del
        // Estado (6.2)]
        PdfPTable banner = new PdfPTable(2);
        banner.setWidthPercentage(100);
        banner.setWidths(new float[] { 3.8f, 6.2f });
        banner.setSpacingAfter(25);

        // Intentar cargar y escalar el logo con un tamaño más protagónico (85x85 pt
        // máx)
        Image logoCelda = null;
        try {
            // Apunta directamente al nuevo nombre de archivo dentro del Classpath de
            // Spring Boot
            org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource(
                    "static/img/control_medico_sistema_familiar.png");

            if (resource.exists()) {
                logoCelda = Image.getInstance(resource.getURL());
                logoCelda.scaleToFit(85, 85); // Mantiene el tamaño ideal de la tarjeta blanca
                logoCelda.setAlignment(Element.ALIGN_CENTER);
            } else {
                System.err.println(
                        "[PDF] Alerta: El archivo 'control_medico_sistema_familiar.png' no existe en src/main/resources/static/img/");
            }
        } catch (Exception e) {
            System.err.println("[PDF] Error crítico al cargar logo en banner: " + e.getMessage());
        }

        // =========================================================================
        // COLUMNA 1: CONTENEDOR DE LOGO INTEGRADO CON MARGEN INTERNO EXACTO
        // =========================================================================
        PdfPCell celdaIzquierdaContenedor = new PdfPCell();
        celdaIzquierdaContenedor.setBackgroundColor(azulPrimario);
        celdaIzquierdaContenedor.setBorder(Rectangle.NO_BORDER);
        celdaIzquierdaContenedor.setPadding(12);
        celdaIzquierdaContenedor.setVerticalAlignment(Element.ALIGN_MIDDLE);

        // Creamos una subtabla interna de 1x1 que actuará como la tarjeta blanca
        PdfPTable tarjetaBlanca = new PdfPTable(1);
        tarjetaBlanca.setWidthPercentage(100);

        PdfPCell cuerpoTarjeta = new PdfPCell();
        cuerpoTarjeta.setBackgroundColor(java.awt.Color.WHITE);
        cuerpoTarjeta.setPadding(4f);

        cuerpoTarjeta.setHorizontalAlignment(Element.ALIGN_CENTER);
        cuerpoTarjeta.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cuerpoTarjeta.setBorderColor(new java.awt.Color(240, 244, 248));
        cuerpoTarjeta.setBorderWidth(1f);

        if (logoCelda != null) {
            logoCelda.setWidthPercentage(100);
            cuerpoTarjeta.addElement(logoCelda);
        } else {
            cuerpoTarjeta.addElement(new Paragraph("LOGOTIPO", fontTablaHeader));
        }

        tarjetaBlanca.addCell(cuerpoTarjeta);
        celdaIzquierdaContenedor.addElement(tarjetaBlanca); // Metemos la tarjeta en la celda del banner
        banner.addCell(celdaIzquierdaContenedor);

        // =========================================================================
        // COLUMNA 2: TEXTOS Y METADATOS (Alineados a la derecha como en tu imagen)
        // =========================================================================
        PdfPCell celdaDerechaTextos = new PdfPCell();
        celdaDerechaTextos.setBorder(Rectangle.NO_BORDER);
        celdaDerechaTextos.setBackgroundColor(azulPrimario);
        celdaDerechaTextos.setPaddingTop(16);
        celdaDerechaTextos.setPaddingBottom(16);
        celdaDerechaTextos.setPaddingRight(20);
        celdaDerechaTextos.setVerticalAlignment(Element.ALIGN_MIDDLE);

        // Párrafo contenedor para alinear todo a la derecha limpiamente
        Paragraph pTextosDerecha = new Paragraph();
        pTextosDerecha.setAlignment(Element.ALIGN_RIGHT);

        // Título Principal
        Paragraph pTitulo = new Paragraph("ESTADO DE CUENTA INDIVIDUAL", fontHeaderTitulo);
        pTitulo.setAlignment(Element.ALIGN_RIGHT);
        pTextosDerecha.add(pTitulo);

        // Subtítulo / Asignado a
        Paragraph pAsignado = new Paragraph("Asignado a: " + familiar.getNombre(), fontHeaderSub);
        pAsignado.setAlignment(Element.ALIGN_RIGHT);
        pAsignado.setSpacingBefore(2f); // Control sutil de separación
        pTextosDerecha.add(pAsignado);

        // Fecha de Emisión
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Paragraph pEmision = new Paragraph("Emisión: " + java.time.LocalDate.now().format(formatter), fontHeaderMeta);
        pEmision.setAlignment(Element.ALIGN_RIGHT);
        pTextosDerecha.add(pEmision);

        // NUEVA ETIQUETA: Estado del Saldo (Línea independiente)
        Paragraph pEstado = new Paragraph("Estado: v2.0 Saldo Actualizado al Día", fontHeaderMeta);
        pEstado.setAlignment(Element.ALIGN_RIGHT);
        pEstado.setSpacingBefore(1f);
        pTextosDerecha.add(pEstado);

        // Agregar contenedor a la celda
        celdaDerechaTextos.addElement(pTextosDerecha);
        banner.addCell(celdaDerechaTextos);

        document.add(banner);

        // --- 6. TABLA 1: RESUMEN DE BALANCE INDIVIDUAL ---
        Paragraph secResumen = new Paragraph("RESUMEN DE BALANCE INDIVIDUAL", fontSeccion);
        secResumen.setSpacingAfter(8);
        document.add(secResumen);

        PdfPTable tablaConceptos = new PdfPTable(2);
        tablaConceptos.setWidthPercentage(100);
        tablaConceptos.setWidths(new float[] { 7.0f, 3.0f });
        tablaConceptos.setSpacingAfter(25);

        tablaConceptos.addCell(crearCeldaHeader("Concepto / Rubro de Evaluación", fontTablaHeader, azulClaro));
        tablaConceptos.addCell(crearCeldaHeader("Balance ($)", fontTablaHeader, azulClaro));

        tablaConceptos.addCell(crearCeldaComun("Total de Gastos Médicos del Grupo Familiar:", fontTablaDataGris, 8));
        tablaConceptos.addCell(crearCeldaMonto(df.format(totalGastosGrupo), fontTablaData, Element.ALIGN_RIGHT));

        tablaConceptos.addCell(crearCeldaComun("Cuota Individual Alícuota Obligatoria (Total / " + totalMiembros + "):",
                fontTablaDataGris, 8));
        tablaConceptos.addCell(crearCeldaMonto(df.format(cuotaIndividual), fontTablaData, Element.ALIGN_RIGHT));

        tablaConceptos.addCell(crearCeldaComun("Total de Aportaciones Recaudadas a la Fecha:", fontTablaDataGris, 8));
        tablaConceptos.addCell(
                crearCeldaMonto(df.format(totalAportadoFamiliar), fontTablaData, verdeExito, Element.ALIGN_RIGHT));

        if (tieneDeuda) {
            tablaConceptos.addCell(crearCeldaComun("CANTIDAD PENDIENTE POR COMPLETAR:", fontTablaHeader, 8));
            tablaConceptos.addCell(
                    crearCeldaMonto(df.format(cantidadFaltante), fontTablaData, rojoAlerta, Element.ALIGN_RIGHT));
        } else {
            tablaConceptos
                    .addCell(crearCeldaComun("BALANCE INDIVIDUAL COMPLETADO (SALDO A FAVOR):", fontTablaHeader, 8));
            tablaConceptos.addCell(crearCeldaMonto(df.format(cantidadFaltante.abs()), fontTablaData, verdeExito,
                    Element.ALIGN_RIGHT));
        }
        document.add(tablaConceptos);

        // --- 7. TABLA 2: HISTORIAL DETALLADO DE APORTACIONES ---
        Paragraph seccionAportaciones = new Paragraph("HISTORIAL DE APORTACIONES REGISTRADAS", fontSeccion);
        seccionAportaciones.setSpacingAfter(8);
        document.add(seccionAportaciones);

        PdfPTable tablaAportaciones = new PdfPTable(3);
        tablaAportaciones.setWidthPercentage(100);
        tablaAportaciones.setWidths(new float[] { 2.0f, 5.5f, 2.5f });

        tablaAportaciones.addCell(crearCeldaHeader("Fecha de Pago", fontTablaHeader, azulClaro));
        tablaAportaciones
                .addCell(crearCeldaHeader("Descripción / Concepto del Movimiento", fontTablaHeader, azulClaro));
        tablaAportaciones.addCell(crearCeldaHeader("Monto Transacción", fontTablaHeader, azulClaro));

        if (aportaciones == null || aportaciones.isEmpty()) {
            PdfPCell celdaVacia = new PdfPCell(new Paragraph(
                    "No se registran transacciones ni aportaciones para este miembro en el período actual.",
                    fontTablaDataGris));
            celdaVacia.setColspan(3);
            celdaVacia.setPadding(12);
            celdaVacia.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaVacia.setBorderColor(new java.awt.Color(226, 232, 240));
            tablaAportaciones.addCell(celdaVacia);
        } else {
            boolean filaPar = false;
            for (Movimiento mov : aportaciones) {
                if (mov != null) {
                    java.awt.Color fondoFila = filaPar ? new java.awt.Color(247, 250, 252) : java.awt.Color.WHITE;
                    String fechaStr = mov.getFecha() != null ? mov.getFecha().format(formatter) : "";
                    String descStr = mov.getDescripcion() != null ? mov.getDescripcion() : "Aportación Regular";
                    String montoStr = mov.getMonto() != null ? df.format(mov.getMonto()) : "$0.00";

                    tablaAportaciones.addCell(crearCeldaComun(fechaStr, fontTablaData, fondoFila, 8));
                    tablaAportaciones.addCell(crearCeldaComun(descStr, fontTablaDataGris, fondoFila, 8));
                    tablaAportaciones.addCell(
                            crearCeldaMonto(montoStr, fontTablaData, verdeExito, fondoFila, Element.ALIGN_RIGHT));
                    filaPar = !filaPar;
                }
            }
        }
        document.add(tablaAportaciones);

        // --- 8. LÍNEAS DE FIRMA Y CIERRE FORMAL ---
        PdfPTable tablaFirmas = new PdfPTable(2);
        tablaFirmas.setWidthPercentage(100);
        tablaFirmas.setSpacingBefore(45);

        PdfPCell f1 = new PdfPCell();
        f1.setBorder(Rectangle.NO_BORDER);
        Paragraph pLine1 = new Paragraph("_________________________________\n", fontTablaDataGris);
        pLine1.setAlignment(Element.ALIGN_CENTER);
        pLine1.add(new Chunk("Firma de Conformidad\n", fontTablaHeader));
        pLine1.add(new Chunk("Familiar Aportador Asignado", fontTablaDataGris));
        f1.addElement(pLine1);
        tablaFirmas.addCell(f1);

        PdfPCell f2 = new PdfPCell();
        f2.setBorder(Rectangle.NO_BORDER);
        Paragraph pLine2 = new Paragraph("_________________________________\n", fontTablaDataGris);
        pLine2.setAlignment(Element.ALIGN_CENTER);
        pLine2.add(new Chunk("Sello de Validación\n", fontTablaHeader));
        pLine2.add(new Chunk("Administración Control Médico", fontTablaDataGris));
        f2.addElement(pLine2);
        tablaFirmas.addCell(f2);

        document.add(tablaFirmas);
        document.close();

        return out.toByteArray();
    }

    // =========================================================================
    // MÉTODOS AUXILIARES ELEGANTES PARA REFACTORIZAR CELDAS (OPENPDF)
    // =========================================================================
    private PdfPCell crearCeldaHeader(String texto, Font font, java.awt.Color colorFondo) {
        PdfPCell cell = new PdfPCell(new Paragraph(texto, font));
        cell.setPadding(8);
        cell.setBackgroundColor(colorFondo);
        cell.setBorderColor(new java.awt.Color(203, 213, 224));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(2f);
        return cell;
    }

    private PdfPCell crearCeldaComun(String texto, Font font, int padding) {
        return crearCeldaComun(texto, font, java.awt.Color.WHITE, padding);
    }

    private PdfPCell crearCeldaComun(String texto, Font font, java.awt.Color colorFondo, int padding) {
        PdfPCell cell = new PdfPCell(new Paragraph(texto, font));
        cell.setPadding(padding);
        cell.setBackgroundColor(colorFondo);
        cell.setBorderColor(new java.awt.Color(226, 232, 240));
        cell.setBorder(Rectangle.BOTTOM);
        return cell;
    }

    private PdfPCell crearCeldaMonto(String texto, Font font, int alineacion) {
        return crearCeldaMonto(texto, font, font.getColor(), java.awt.Color.WHITE, alineacion);
    }

    private PdfPCell crearCeldaMonto(String texto, Font font, java.awt.Color colorTexto, int alineacion) {
        return crearCeldaMonto(texto, font, colorTexto, java.awt.Color.WHITE, alineacion);
    }

    private PdfPCell crearCeldaMonto(String texto, Font font, java.awt.Color colorTexto, java.awt.Color colorFondo,
            int alineacion) {
        Font fontClonada = new Font(font);
        fontClonada.setColor(colorTexto);
        PdfPCell cell = new PdfPCell(new Paragraph(texto, fontClonada));
        cell.setPadding(8);
        cell.setBackgroundColor(colorFondo);
        cell.setHorizontalAlignment(alineacion);
        cell.setBorderColor(new java.awt.Color(226, 232, 240));
        cell.setBorder(Rectangle.BOTTOM);
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
                log.warn("[LOG-CATEGORIA-DIAGNOSTICO] El objeto Categoria recibido en el @ModelAttribute es NULL.");
            }
        } else {
            log.error("[LOG-CATEGORIA-DIAGNOSTICO] El objeto Movimiento recibido es completamente NULL.");
        }

        // 1. Validar errores de binding de Spring
        if (result.hasErrors()) {
            log.error("[LOG-MOVIMIENTO] Error de binding en el formulario: {}", result.getAllErrors());
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
                log.warn("[LOG-CATEGORIA-DIAGNOSTICO] Acceso no autorizado para: {}", username);
                return "redirect:/access-denied";
            }

            // 1. Verificación de seguridad inicial (Evita el NullPointerException de raíz)
            if (movimiento == null) {
                log.error("[LOG-MOVIMIENTO] El objeto movimiento recibido en el controlador es NULL.");
                redirectAttributes.addFlashAttribute("mensajeError", "El movimiento no pudo ser procesado.");
                return "redirect:/";
            }

            // 2. Obtener el familiar seleccionado en el modal del formulario de forma
            // segura
            if (movimiento.getFamiliar() == null || movimiento.getFamiliar().getId() == null) {
                log.warn("[LOG-MOVIMIENTO] Intento de guardar sin especificar un familiar.");
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
                        log.error("[LOG-CATEGORIA-DIAGNOSTICO] La categoría con ID {} NO EXISTE en la base de datos.",
                                catId);
                        movimiento.setCategoria(null);
                    }
                } else {
                    log.warn(
                            "[LOG-CATEGORIA-DIAGNOSTICO] Se envió un GASTO pero el ID de categoría llegó vacío/nulo.");
                    movimiento.setCategoria(null);
                }
            }

            // Log de estado final previo a guardar
            log.info(
                    "[LOG-MOVIMIENTO] Guardando en DB -> Tipo: '{}', Familiar: '{}', Categoria asignada final: '{}', Monto: ${}",
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

    // =========================================================================
    // HELPER OPTIMIZADO: MARCA DE AGUA Y PAGINACIÓN DINÁMICA (X de Y)
    // =========================================================================
    class DiseñoPdfHelper extends com.lowagie.text.pdf.PdfPageEventHelper {

        private Font fontWatermark;
        private Font fontFooter;
        private com.lowagie.text.pdf.PdfTemplate totalPaginasTemplate;

        public DiseñoPdfHelper() {
            // Fuente de la marca de agua
            this.fontWatermark = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 46, Font.BOLD,
                    new java.awt.Color(13, 110, 253));
            // Fuente sutil para el pie de página
            this.fontFooter = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL,
                    new java.awt.Color(128, 128, 128));
        }

        // 1. Inicializamos el template asíncrono cuando el documento se abre
        //@Override
        // public void onOpenPage(com.lowagie.text.pdf.PdfWriter writer, Document document) {
        //     if (totalPaginasTemplate == null) {
        //         totalPaginasTemplate = writer.getDirectContent().createTemplate(30, 12);
        //     }
        // }

        @Override
        public void onEndPage(com.lowagie.text.pdf.PdfWriter writer, Document document) {
            com.lowagie.text.pdf.PdfContentByte cbCanvas = writer.getDirectContent();

            // 🌟 CONTROL DE SEGURIDAD FALTANTE: Inicialización perezosa si viene null
            if (this.totalPaginasTemplate == null) {
                this.totalPaginasTemplate = cbCanvas.createTemplate(30, 12);
            }

            // -----------------------------------------------------------------
            // A. ESTAMPAR MARCA DE AGUA (En capa superior con opacidad)
            // -----------------------------------------------------------------
            cbCanvas.saveState();
            cbCanvas.beginText();
            cbCanvas.setFontAndSize(fontWatermark.getCalculatedBaseFont(false), 46);

            com.lowagie.text.pdf.PdfGState gState = new com.lowagie.text.pdf.PdfGState();
            gState.setFillOpacity(0.08f);
            gState.setStrokeOpacity(0.08f);
            cbCanvas.setGState(gState);

            cbCanvas.setColorFill(new java.awt.Color(13, 110, 253));

            float x = PageSize.A4.getWidth() / 2;
            float y = PageSize.A4.getHeight() / 2;

            cbCanvas.showTextAligned(Element.ALIGN_CENTER, "VALIDADO - CONTROL MÉDICO", x, y, 45);
            cbCanvas.endText();
            cbCanvas.restoreState();

            // -----------------------------------------------------------------
            // B. ESTAMPAR PIE DE PÁGINA DINÁMICO (Página X de [Template])
            // -----------------------------------------------------------------
            cbCanvas.saveState();
            cbCanvas.beginText();
            cbCanvas.setFontAndSize(fontFooter.getCalculatedBaseFont(false), 8);
            cbCanvas.setColorFill(new java.awt.Color(128, 128, 128));

            String textoPagina = "Página " + writer.getPageNumber() + " de ";
            float anchoTexto = fontFooter.getCalculatedBaseFont(false).getWidthPoint(textoPagina, 8);

            float xFooter = PageSize.A4.getWidth() - 85;
            float yFooter = 25;

            cbCanvas.showTextAligned(Element.ALIGN_LEFT, textoPagina, xFooter, yFooter, 0);
            cbCanvas.endText();
            cbCanvas.restoreState();

            // Ahora estamos 100% seguros de que totalPaginasTemplate no es null
            cbCanvas.addTemplate(totalPaginasTemplate, xFooter + anchoTexto, yFooter - 1);
        }

        // 2. Cuando el documento se termina de escribir, rellenamos el valor "Y"
        // global
        @Override
        public void onCloseDocument(com.lowagie.text.pdf.PdfWriter writer, Document document) {
            if (totalPaginasTemplate != null) {
                totalPaginasTemplate.beginText();
                totalPaginasTemplate.setFontAndSize(fontFooter.getCalculatedBaseFont(false), 8);
                totalPaginasTemplate.setColorFill(new java.awt.Color(128, 128, 128));
                // Escribe el número total acumulado de páginas
                totalPaginasTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
                totalPaginasTemplate.endText();
            }
        }
    }
}