package com.example.recordatorios.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Resultado de una operación remota, ya interpretado.
 * ÚNICO punto de traducción de códigos HTTP y excepciones: el resto de la app
 * (repositorio, ViewModel, pantallas) solo ve estas variantes.
 */
sealed interface ResultadoRemoto<out T> {
    data class Exito<T>(val datos: T) : ResultadoRemoto<T>

    /** 4xx: el servidor rechazó la solicitud (datos inválidos, recurso inexistente...). */
    data class SolicitudInvalida(val codigo: Int, val detalle: String?) : ResultadoRemoto<Nothing>

    /** 5xx: el servidor falló al procesar una solicitud válida. */
    data class ErrorServidor(val codigo: Int) : ResultadoRemoto<Nothing>

    /** El servidor no respondió a tiempo. */
    data object Timeout : ResultadoRemoto<Nothing>

    /** No se pudo llegar al servidor (sin red, DNS, conexión rechazada). */
    data object SinConexion : ResultadoRemoto<Nothing>

    /** Cualquier otra situación (respuesta ilegible, código inesperado). */
    data class ErrorDesconocido(val descripcion: String) : ResultadoRemoto<Nothing>
}

/**
 * Ejecuta la llamada y la traduce a [ResultadoRemoto]. El cuerpo puede ser null
 * (por ejemplo, 204 No Content).
 */
suspend fun <T> llamarApi(llamada: suspend () -> Response<T>): ResultadoRemoto<T?> =
    try {
        interpretarRespuesta(llamada())
    } catch (e: CancellationException) {
        throw e   // la cancelación de corrutinas nunca se convierte en error
    } catch (e: Throwable) {
        interpretarExcepcion(e)
    }

fun <T> interpretarRespuesta(respuesta: Response<T>): ResultadoRemoto<T?> {
    val codigo = respuesta.code()
    return when (codigo) {
        in 200..299 -> ResultadoRemoto.Exito(respuesta.body())
        in 400..499 -> ResultadoRemoto.SolicitudInvalida(codigo, leerDetalle(respuesta))
        in 500..599 -> ResultadoRemoto.ErrorServidor(codigo)
        else -> ResultadoRemoto.ErrorDesconocido("Respuesta inesperada del servidor (HTTP $codigo)")
    }
}

fun interpretarExcepcion(error: Throwable): ResultadoRemoto<Nothing> = when (error) {
    // SocketTimeoutException es un IOException: se evalúa ANTES
    is SocketTimeoutException -> ResultadoRemoto.Timeout
    is IOException -> ResultadoRemoto.SinConexion
    is SerializationException -> ResultadoRemoto.ErrorDesconocido("La respuesta del servidor no tiene el formato esperado")
    else -> ResultadoRemoto.ErrorDesconocido(error.message ?: error::class.java.simpleName)
}

private fun leerDetalle(respuesta: Response<*>): String? =
    try {
        respuesta.errorBody()?.string()?.takeIf { it.isNotBlank() }?.take(200)
    } catch (_: IOException) {
        null
    }

/** Errores transitorios: conviene cortar la sincronización y reintentar más tarde. */
val ResultadoRemoto<*>.esTransitorio: Boolean
    get() = this is ResultadoRemoto.SinConexion || this is ResultadoRemoto.Timeout ||
        this is ResultadoRemoto.ErrorServidor

/** Texto para la persona usuaria: sin códigos técnicos ni nombres de excepciones. */
fun ResultadoRemoto<*>.mensajeParaUsuario(): String = when (this) {
    is ResultadoRemoto.Exito -> "Sincronizado"
    is ResultadoRemoto.SolicitudInvalida -> "El servidor rechazó los datos enviados. Revisá el recordatorio y reintentá."
    is ResultadoRemoto.ErrorServidor -> "El servidor tuvo un problema. Los cambios quedan guardados; reintentá más tarde."
    ResultadoRemoto.Timeout -> "El servidor tardó demasiado en responder. Se reintentará."
    ResultadoRemoto.SinConexion -> "Sin conexión con el servidor. Los cambios se enviarán al recuperar la red."
    is ResultadoRemoto.ErrorDesconocido -> "No se pudo sincronizar por un error inesperado. Reintentá más tarde."
}
