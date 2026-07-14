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
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ControlMedicoService {

    private final MovimientoRepository movimientoRepository;
    private final FamiliarRepository familiarRepository;
    private final CategoriaRepository categoriaRepository;

    public ControlMedicoService(MovimientoRepository movimientoRepository, 
                                FamiliarRepository familiarRepository, 
                                CategoriaRepository categoriaRepository) {
        this.movimientoRepository = movimientoRepository;
        this.familiarRepository = familiarRepository;
        this.categoriaRepository = categoriaRepository;
    }

    // --- LISTADOS Y PERSISTENCIA ---

    public List<Familiar> listarFamiliares() { return familiarRepository.findAll(); }
    public List<Categoria> listarCategorias() { return categoriaRepository.findAll(); }
    public List<Categoria> obtenerTodasLasCategorias() { return categoriaRepository.findAll(); }
    public List<Familiar> obtenerTodosLosFamiliares() { return familiarRepository.findAll(); }

    public Movimiento guardarMovimiento(Movimiento movimiento) { return movimientoRepository.save(movimiento); }
    public Movimiento registrarMovimiento(Movimiento movimiento) { return movimientoRepository.save(movimiento); }
    public Familiar guardarFamiliar(Familiar familiar) { return familiarRepository.save(familiar); }
    public void eliminarMovimiento(Long id) { movimientoRepository.deleteById(id); }

    // --- CÁLCULOS FINANCIEROS POR INQUILINO (ANTI-NULL PARA SYS_ADMIN) ---

    public BigDecimal calcularTotalAportado(Long familiaId) {
        if (familiaId == null) return BigDecimal.ZERO;
        BigDecimal total = movimientoRepository.sumAportacionesByFamilia(familiaId);
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal calcularTotalGastado(Long familiaId) {
        if (familiaId == null) return BigDecimal.ZERO;
        BigDecimal total = movimientoRepository.sumGastosByFamilia(familiaId);
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal calcularBalanceDisponible(Long familiaId) {
        if (familiaId == null) return BigDecimal.ZERO;
        return calcularTotalAportado(familiaId).subtract(calcularTotalGastado(familiaId));
    }

    public BigDecimal calcularCuotaFamiliar(Long familiaId) {
        if (familiaId == null) return BigDecimal.ZERO;
        BigDecimal gastoTotal = calcularTotalGastado(familiaId);
        long cantidadAportadores = contarAportadoresPorFamilia(familiaId);
        if (cantidadAportadores == 0) return BigDecimal.ZERO;
        return gastoTotal.divide(BigDecimal.valueOf(cantidadAportadores), 2, RoundingMode.HALF_UP);
    }

    // --- PAGINACIÓN FILTRADA ---

    public Page<Movimiento> obtenerMovimientosPorFamiliaPaginado(Long familiaId, int numeroPagina) {
        Pageable pageable = PageRequest.of(numeroPagina, 10);
        if (familiaId == null) return Page.empty(pageable);
        return movimientoRepository.findByFamiliarFamiliaIdOrderByFechaDescIdAsc(familiaId, pageable);
    }

    // --- REPORTES INDIVIDUALES Y TOTALES GLOBALES ---

    public List<Movimiento> obtenerMovimientosPorFamiliar(Long familiarId) {
        return movimientoRepository.findByFamiliarIdOrderByFechaDesc(familiarId);
    }

    public Map<String, BigDecimal> obtenerResumenFinancieroPorFamiliar(Long familiarId) {
        BigDecimal aportado = movimientoRepository.sumAportacionesByFamiliar(familiarId);
        BigDecimal gastado = movimientoRepository.sumGastosByFamiliar(familiarId);
        BigDecimal balanceIndividual = (aportado != null ? aportado : BigDecimal.ZERO)
                .subtract(gastado != null ? gastado : BigDecimal.ZERO);

        Map<String, BigDecimal> resumen = new HashMap<>();
        resumen.put("aportado", aportado != null ? aportado : BigDecimal.ZERO);
        resumen.put("gastado", gastado != null ? gastado : BigDecimal.ZERO);
        resumen.put("balance", balanceIndividual);
        return resumen;
    }

    public Map<String, BigDecimal> obtenerResumenFinancieroGlobal() {
        BigDecimal aportado = movimientoRepository.sumTotalAportaciones();
        BigDecimal gastado = movimientoRepository.sumTotalGastos();
        BigDecimal totalAportado = aportado != null ? aportado : BigDecimal.ZERO;
        BigDecimal totalGastado = gastado != null ? gastado : BigDecimal.ZERO;

        Map<String, BigDecimal> resumen = new HashMap<>();
        resumen.put("aportado", totalAportado);
        resumen.put("gastado", totalGastado);
        resumen.put("balance", totalAportado.subtract(totalGastado));
        return resumen;
    }

    public Page<Movimiento> obtenerTodosLosMovimientos(int numeroPagina) {
        Pageable pageable = PageRequest.of(numeroPagina, 10);
        return movimientoRepository.findAllByOrderByFechaDescIdAsc(pageable);
    }

    public Optional<Familiar> obtenerFamiliarPorId(Long id) { return familiarRepository.findById(id); }

    public long contarAportadoresPorFamilia(Long familiaId) {
        if (familiaId == null) return 0;
        return familiarRepository.countByFamiliaIdAndTipoMembresia(familiaId, TipoMembresia.APORTADOR);
    }
}