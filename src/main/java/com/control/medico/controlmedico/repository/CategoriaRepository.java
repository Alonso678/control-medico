package com.control.medico.controlmedico.repository;

import com.control.medico.controlmedico.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    // Ya incluye todos los métodos CRUD por defecto
}