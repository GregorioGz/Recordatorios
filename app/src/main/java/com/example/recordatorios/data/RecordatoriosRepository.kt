package com.example.recordatorios.data

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
 * solo reciben modelos de dominio: nunca Entity, documentos ni excepciones de Firebase.
 * Estrategia local-first: las lecturas salen siempre de Room y las escrituras
 * se aplican primero en Room; la sincronización con la nube es posterior.
 * Todo se limita a la cuenta de la sesión activa: sin sesión no se expone nada.
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

    /** Igual que [sincronizar], pero en el alcance de la sesión (no se cancela al salir de una pantalla). */
    fun sincronizarEnSegundoPlano()

    /** Cambios que la nube todavía no confirmó (para avisar antes de cerrar sesión). */
    suspend fun cantidadPendientes(): Int

    /**
     * Cierra la sesión: opcionalmente sincroniza antes; después cancela la sincronización
     * y los listeners, borra de Room los datos de la cuenta y cierra la sesión del proveedor.
     * Las preferencias de DataStore (tema, orden...) son del dispositivo y se conservan.
     */
    suspend fun cerrarSesion(sincronizarAntes: Boolean): ResultadoCierreSesion

    fun categoriasDisponibles(): Set<Categoria>
}

sealed interface ResultadoCierreSesion {
    data object Cerrada : ResultadoCierreSesion

    /** Se pidió sincronizar antes de salir pero quedaron cambios sin enviar: la sesión sigue abierta. */
    data class QuedanPendientes(val cantidad: Int, val motivo: String?) : ResultadoCierreSesion
}

/**
 * Herramientas del menú ⋮ de pruebas (solo en el flavor dev) para comprobar estados
 * de la interfaz, la sincronización y las reglas de seguridad sin tocar el código.
 */
interface SimuladorDeEscenarios {
    fun simularListaVacia()
    fun simularErrorEnProximaLectura()
    fun simularErrorEnProximoGuardado()

    /** Quita las simulaciones y agrega los ejemplos que falten a la cuenta activa. Devuelve cuántos agregó. */
    suspend fun cargarDatosDeEjemplo(): Int

    /**
     * Marca todo como pendiente y lanza [cantidad] sincronizaciones a la vez.
     * Devuelve un resumen (escrituras enviadas, documentos en la nube y en el dispositivo)
     * para comprobar que un reintento no genera duplicados.
     */
    suspend fun probarSincronizacionConcurrente(cantidad: Int = 3): String

    /** Lee users/{otroUid}/reminders directamente en Firestore. Devuelve el resultado explicado. */
    suspend fun intentarLeerOtraCuenta(otroUid: String): String

    /** Intenta crear un documento en users/{otroUid}/reminders directamente en Firestore. */
    suspend fun intentarEscribirEnOtraCuenta(otroUid: String): String
}
