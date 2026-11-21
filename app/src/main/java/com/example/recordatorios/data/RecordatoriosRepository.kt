package com.example.recordatorios.data

import com.example.recordatorios.data.remote.EscenarioRemoto
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.EstadoSincronizacion
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de la capa de datos. Los ViewModel dependen SOLO de esta interfaz y
 * solo reciben modelos de dominio: nunca Entity, DTO, códigos HTTP ni excepciones de red.
 * Estrategia local-first: las lecturas salen siempre de Room y las escrituras
 * se aplican primero en Room; la sincronización con el servidor es posterior.
 */
interface RecordatoriosRepository {
    /** Listado desde Room. `prioridad` null = todas (filtro resuelto por el DAO). */
    fun observarRecordatorios(prioridad: Prioridad? = null): Flow<List<Recordatorio>>
    fun observarRecordatorio(id: String): Flow<Recordatorio?>

    /** Estado global: conexión, sincronización en curso, pendientes y último error. */
    val estadoSincronizacion: StateFlow<EstadoSincronizacion>

    suspend fun crear(borrador: BorradorRecordatorio): Recordatorio
    suspend fun actualizar(id: String, borrador: BorradorRecordatorio): Recordatorio
    suspend fun cambiarEstado(id: String, estado: EstadoRecordatorio)
    suspend fun eliminar(id: String)

    /** Envía los pendientes y descarga los cambios remotos. Nunca corre dos veces en paralelo. */
    suspend fun sincronizar()

    /** Igual que [sincronizar], pero en el alcance de la aplicación (no se cancela al salir de una pantalla). */
    fun sincronizarEnSegundoPlano()

    fun categoriasDisponibles(): Set<Categoria>
}

/**
 * Mecanismo de simulación (menú ⋮ de pruebas) para comprobar los estados de la
 * interfaz sin tocar el código: lista vacía, errores de lectura o guardado, etc.
 */
interface SimuladorDeEscenarios {
    fun simularListaVacia()
    fun simularErrorEnProximaLectura()
    fun simularErrorEnProximoGuardado()

    /** Quita las simulaciones locales y vuelve a insertar los ejemplos que falten. */
    suspend fun restaurarDatosDeEjemplo()

    /** Escenario activo del servidor simulado (null si se usa un servidor real). */
    val escenarioRemoto: StateFlow<EscenarioRemoto?>

    /** Devuelve false si no hay servidor simulado (URL real configurada). */
    fun cambiarEscenarioRemoto(escenario: EscenarioRemoto): Boolean

    /**
     * Marca todo como pendiente y lanza [cantidad] sincronizaciones a la vez.
     * Devuelve un resumen (PUT recibidos y registros en el servidor) para comprobar
     * que no se generan duplicados.
     */
    suspend fun probarSincronizacionConcurrente(cantidad: Int = 3): String
}
