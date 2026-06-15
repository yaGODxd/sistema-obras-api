package com.example.sistemaobras.entity

import jakarta.persistence.*

@Entity
@Table(name = "secretarias")
data class Secretaria(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "nome", nullable = false)
    val nome: String = ""
)