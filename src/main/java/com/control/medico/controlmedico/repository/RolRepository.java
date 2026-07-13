package com.control.medico.controlmedico.repository;

import com.control.medico.controlmedico.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {
    Optional<Rol> findByDescripcion(String descripcion);
}
