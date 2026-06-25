package com.control.medico.controlmedico.repository;

import com.control.medico.controlmedico.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    
    // Ya incluye todos los métodos CRUD por defecto
    Optional<Categoria> findByNombreIgnoreCase(String nombre);
}