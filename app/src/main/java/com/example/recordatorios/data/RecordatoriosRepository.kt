package com.example.recordatorios.data

import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.Recordatorio

/**
 * Contrato de la fuente de datos. Los ViewModel dependen SOLO de esta interfaz,
 * así más adelante se puede reemplazar la implementación en memoria por Room
 * o por un servicio remoto sin tocar pantallas ni componentes.
 */
interface RecordatoriosRepository {
    suspend fun obtenerTodos(): List<Recordatorio>
    suspend fun obtenerPorId(id: Int): Recordatorio?
    suspend fun guardar(borrador: BorradorRecordatorio): Recordatorio
    fun categoriasDisponibles(): Set<Categoria>
}

/**
 * Mecanismo de simulación para comprobar todos los estados de la interfaz
 * (vacío, error de carga, error de guardado) sin base de datos ni red.
 */
interface SimuladorDeEscenarios {
    fun simularErrorEnProximaCarga()
    fun simularErrorEnProximoGuardado()
    fun vaciarDatos()
    fun restaurarDatosDeEjemplo()
}
