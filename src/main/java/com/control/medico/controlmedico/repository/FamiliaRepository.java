package com.control.medico.controlmedico.repository;

import com.control.medico.controlmedico.model.Familia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamiliaRepository extends JpaRepository<Familia, Long> {
    
    // Consulta optimizada para la vista de SYS_ADMIN con paginación
    Page<Familia> findAllByOrderByFechaCreacionDesc(Pageable pageable);
}
