package com.control.medico.controlmedico.repository;

import com.control.medico.controlmedico.model.Movimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    Page<Movimiento> findByFamiliarFamiliaIdOrderByFechaDescIdAsc(Long familiaId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM Movimiento m WHERE m.tipo = 'APORTACION' AND m.familiar.familia.id = :familiaId")
    BigDecimal sumAportacionesByFamilia(@Param("familiaId") Long familiaId);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM Movimiento m WHERE m.tipo = 'GASTO' AND m.familiar.familia.id = :familiaId")
    BigDecimal sumGastosByFamilia(@Param("familiaId") Long familiaId);

    Page<Movimiento> findAllByOrderByFechaDescIdAsc(Pageable pageable);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM Movimiento m WHERE m.tipo = 'APORTACION'")
    BigDecimal sumTotalAportaciones();

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM Movimiento m WHERE m.tipo = 'GASTO'")
    BigDecimal sumTotalGastos();

    @Query("SELECT m FROM Movimiento m WHERE m.familiar.id = :familiarId ORDER BY m.fecha DESC, m.id ASC")
    List<Movimiento> findByFamiliarIdOrderByFechaDesc(@Param("familiarId") Long familiarId);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM Movimiento m WHERE m.tipo = 'APORTACION' AND m.familiar.id = :familiarId")
    BigDecimal sumAportacionesByFamiliar(@Param("familiarId") Long familiarId);

    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM Movimiento m WHERE m.tipo = 'GASTO' AND m.familiar.id = :familiarId")
    BigDecimal sumGastosByFamiliar(@Param("familiarId") Long familiarId);

    // MÉTODO NUEVO: Recupera las aportaciones de un miembro usando su familiar ID y el tipo ('APORTACION')
    List<Movimiento> findByFamiliarIdAndTipo(Long familiarId, String tipo);
}