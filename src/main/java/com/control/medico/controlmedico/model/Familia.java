package com.control.medico.controlmedico.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "familias", schema = "control_medico")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Familia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "codigo_acceso", nullable = false, unique = true, length = 50)
    private String codigoAcceso;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
