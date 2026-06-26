package com.control.medico.controlmedico.repository;

import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.TipoMembresia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FamiliarRepository extends JpaRepository<Familiar, Long> {
    // Hereda todos los métodos CRUD básicos sin warnings
    Optional<Familiar> findByUsername(String username);

    // Cuenta cuántos familiares pertenecen a una familia específica Y tienen el rol de APORTADOR
    long countByFamiliaIdAndTipoMembresia(Long familiaId, TipoMembresia tipoMembresia);
}