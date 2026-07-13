package com.control.medico.controlmedico.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import jakarta.persistence.Column;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "familiares", schema = "control_medico")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Familiar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String nombre;

    // --- NUEVOS CAMPOS PARA SEGURIDAD MULTI-FAMILIA ---
    @Column(unique = true, length = 50)
    private String username;

    @Column(length = 100)
    private String password;
    
    // Agregar la relación hacia el nuevo objeto Rol:
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Rol role;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_membresia", length = 20, nullable = false)
    private TipoMembresia tipoMembresia; // APORTADOR o BENEFICIARIO

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "familia_id")
    private Familia familia; // Relación con la nueva tabla familias

    @Column(name = "is_administrador")
    private Boolean administrador = false;
}
