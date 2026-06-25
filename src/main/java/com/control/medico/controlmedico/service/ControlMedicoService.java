package com.control.medico.controlmedico.service;

import com.control.medico.controlmedico.model.Categoria;
import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.Movimiento;
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

@Service
public class ControlMedicoService {

    private final MovimientoRepository movimientoRepository;

    private final FamiliarRepository familiarRepository;

    private final CategoriaRepository categoriaRepository;

    ControlMedicoService(MovimientoRepository movimientoRepository, FamiliarRepository familiarRepository, CategoriaRepository categoriaRepository) {
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
        // Validación lógica: Si es aportación, no lleva categoría médica
        if ("APORTACION".equals(movimiento.getTipo())) {
            movimiento.setCategoria(null);
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
}
