package com.control.medico.controlmedico.repository;

import com.control.medico.controlmedico.model.Familiar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamiliarRepository extends JpaRepository<Familiar, Long> {
    // Hereda todos los métodos CRUD básicos sin warnings
}