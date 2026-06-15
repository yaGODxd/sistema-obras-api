package com.example.sistemaobras.repository

import com.example.sistemaobras.entity.Secretaria
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SecretariaRepository : JpaRepository<Secretaria, Int>