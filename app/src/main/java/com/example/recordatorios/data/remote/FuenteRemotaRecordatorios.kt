package com.example.recordatorios.data.remote

import kotlinx.coroutines.flow.Flow

/** Documento remoto sin tipos del proveedor: id + campos como Map (mapeo explícito en Mapeos.kt). */
data class DocumentoRemoto(
    val id: String,
    val datos: Map<String, Any?>
)

/** Cambio recibido en tiempo real desde la nube (hecho en otro dispositivo). */
sealed interface CambioRemoto {
    data class Guardado(val documento: DocumentoRemoto) : CambioRemoto
    data class Eliminado(val id: String) : CambioRemoto
}

/**
 * Fuente remota de los recordatorios de UNA cuenta (users/{uid}/reminders/{id}).
 * Cada operación devuelve un [ResultadoRemoto] ya interpretado.
 */
interface FuenteRemotaRecordatorios {
    /** Lee del servidor todos los recordatorios de la cuenta. */
    suspend fun descargar(uid: String): ResultadoRemoto<List<DocumentoRemoto>>

    /** Crea o reemplaza el documento con ese id (idempotente: un reintento nunca crea otro). */
    suspend fun guardar(uid: String, id: String, datos: Map<String, Any?>): ResultadoRemoto<Unit>

    /** Borra el documento (borrar uno inexistente también es éxito). */
    suspend fun eliminar(uid: String, id: String): ResultadoRemoto<Unit>

    /**
     * Cambios en tiempo real de la colección de la cuenta. Excluye las escrituras
     * propias todavía no confirmadas. Se cancela al cancelar la recolección.
     * Si el servicio corta el listener, emite el error y termina.
     */
    fun observarCambios(uid: String): Flow<ResultadoRemoto<List<CambioRemoto>>>
}

/**
 * Operaciones SOLO para comprobar las reglas de seguridad (menú de pruebas en dev).
 * Van directo al servicio, sin pasar por el repositorio ni por el filtro de la interfaz.
 */
interface PruebaDeAutorizacion {
    /** Intenta leer users/{otroUid}/reminders. Devuelve la cantidad de documentos leídos. */
    suspend fun leerDeOtraCuenta(otroUid: String): ResultadoRemoto<Int>

    /** Intenta escribir un documento válido en users/{otroUid}/reminders/{id}. */
    suspend fun escribirEnOtraCuenta(otroUid: String, id: String, datos: Map<String, Any?>): ResultadoRemoto<Unit>
}
