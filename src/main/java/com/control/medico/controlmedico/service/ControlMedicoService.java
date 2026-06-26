package com.control.medico.controlmedico.service;

import com.control.medico.controlmedico.model.Categoria;
import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.Movimiento;
import com.control.medico.controlmedico.model.TipoMembresia;
import com.control.medico.controlmedico.repository.CategoriaRepository;
import com.control.medico.controlmedico.repository.FamiliarRepository;
import com.control.medico.controlmedico.repository.MovimientoRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Optional;

@Service
public class ControlMedicoService {

    private final MovimientoRepository movimientoRepository;

    private final FamiliarRepository familiarRepository;

    private final CategoriaRepository categoriaRepository;

    // Constructor para Inyección de Dependencias
    public ControlMedicoService(MovimientoRepository movimientoRepository, FamiliarRepository familiarRepository, CategoriaRepository categoriaRepository) {
        this.movimientoRepository = movimientoRepository;
        this.familiarRepository = familiarRepository;
        this.categoriaRepository = categoriaRepository;
    }

    // --- LISTADOS BÁSICOS Y GUARDADO ---

    public List<Familiar> obtenerTodosLosFamiliares() {
        return familiarRepository.findAll();
    }

    public List<Categoria> obtenerTodasLasCategorias() {
        return categoriaRepository.findAll();
    }

    public List<Movimiento> obtenerTodosLosMovimientos() {
        return movimientoRepository.findAllByOrderByFechaDescIdAsc();
    }

    public Movimiento registrarMovimiento(Movimiento movimiento) {
        // 1. Obtener el usuario autenticado en Spring Security
        String usernameActivo = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();

        Familiar usuarioLogueado = familiarRepository.findByUsername(usernameActivo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado en la sesión actual"));

        // 2. Forzar que el movimiento pertenezca estrictamente a la familia del usuario
        // activo
        movimiento.setFamilia(usuarioLogueado.getFamilia());

        // 3. Validación lógica existente para Aportaciones
        if (movimiento.getTipo() != null &&
                ("APORTACION".equalsIgnoreCase(movimiento.getTipo())
                        || "APORTACIÓN".equalsIgnoreCase(movimiento.getTipo()))) {

            movimiento.setTipo("APORTACION");

            // Obtención dinámica usando el repositorio de categorías que construimos
            Categoria categoriaAportacion = categoriaRepository.findByNombreIgnoreCase("Aportaciones")
                    .orElseThrow(() -> new RuntimeException("La categoría 'Aportaciones' no existe en el catálogo."));

            movimiento.setCategoria(categoriaAportacion);
        }

        return movimientoRepository.save(movimiento);
    }

    // --- LÓGICA DE BALANCES GLOBALES (Para el Dashboard Principal) ---

    public Map<String, BigDecimal> obtenerResumenFinancieroGlobal() {
        BigDecimal totalAportado = movimientoRepository.sumTotalAportaciones();
        BigDecimal totalGastado = movimientoRepository.sumTotalGastos();
        BigDecimal balanceDisponible = totalAportado.subtract(totalGastado);

        Map<String, BigDecimal> resumen = new HashMap<>();
        resumen.put("totalAportado", totalAportado);
        resumen.put("totalGastado", totalGastado);
        resumen.put("balanceDisponible", balanceDisponible);
        return resumen;
    }

    // --- LÓGICA DE REPORTES INDIVIDUALES (Para la pestaña de Reporte Individual) ---

    public List<Movimiento> obtenerMovimientosPorFamiliar(Long familiarId) {
        return movimientoRepository.findByFamiliarIdOrderByFechaDesc(familiarId);
    }

    public Map<String, BigDecimal> obtenerResumenFinancieroPorFamiliar(Long familiarId) {
        BigDecimal aportado = movimientoRepository.sumAportacionesByFamiliar(familiarId);
        BigDecimal gastado = movimientoRepository.sumGastosByFamiliar(familiarId);
        BigDecimal balanceIndividual = aportado.subtract(gastado);

        Map<String, BigDecimal> resumen = new HashMap<>();
        resumen.put("aportado", aportado);
        resumen.put("gastado", gastado);
        resumen.put("balance", balanceIndividual);
        return resumen;
    }

    public void eliminarMovimiento(Long id) {
        movimientoRepository.deleteById(id);
    }

    public Page<Movimiento> obtenerTodosLosMovimientos(int numeroPagina) {
        // Definimos el tamaño de la página en 20 registros
        Pageable pageable = PageRequest.of(numeroPagina, 10);
        return movimientoRepository.findAllByOrderByFechaDescIdAsc(pageable);
    }

    public Optional<Familiar> obtenerFamiliarPorId(Long id) {
        return familiarRepository.findById(id);
    }

    public long contarAportadoresPorFamilia(Long familiaId) {
        // Importa tu Enum TipoMembresia si es necesario
        return familiarRepository.countByFamiliaIdAndTipoMembresia(familiaId, TipoMembresia.APORTADOR);
    }
}
