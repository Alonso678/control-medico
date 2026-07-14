package com.control.medico.controlmedico.repository;

import com.control.medico.controlmedico.model.Familiar;
import com.control.medico.controlmedico.model.TipoMembresia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

import java.util.Optional;

public interface FamiliarRepository extends JpaRepository<Familiar, Long> {
    // Hereda todos los métodos CRUD básicos sin warnings
    Optional<Familiar> findByUsername(String username);

    // Cuenta cuántos familiares pertenecen a una familia específica Y tienen el rol de APORTADOR
    long countByFamiliaIdAndTipoMembresia(Long familiaId, TipoMembresia tipoMembresia);

    @Modifying
    @Query("UPDATE Familiar f SET f.role = (SELECT r FROM Rol r WHERE r.descripcion = 'ROLE_FAMILIAR') WHERE f.role.descripcion = 'ROLE_ADMIN'")
    void quitarAdministradorActual();

    // Obtener los miembros de una familia específica de forma paginada o en lista
    List<Familiar> findByFamiliaId(Long familiaId);
    
    long countByFamiliaId(Long familiaId);
}