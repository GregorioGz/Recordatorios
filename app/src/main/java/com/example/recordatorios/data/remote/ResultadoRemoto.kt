package com.example.recordatorios.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Resultado de una operación remota, ya interpretado.
 * El repositorio, los ViewModel y las pantallas solo ven estas variantes:
 * nunca FirebaseFirestoreException ni sus códigos.
 */
sealed interface ResultadoRemoto<out T> {
    data class Exito<T>(val datos: T) : ResultadoRemoto<T>

    /** No se pudo llegar al servicio (sin red, servicio no disponible). */
    data object SinConexion : ResultadoRemoto<Nothing>

    /** El servicio no respondió a tiempo. */
    data object Timeout : ResultadoRemoto<Nothing>

    /** Las reglas de seguridad rechazaron la operación (PERMISSION_DENIED). */
    data object SinPermiso : ResultadoRemoto<Nothing>

    /** El servicio no acepta la sesión (UNAUTHENTICATED): hay que volver a iniciar sesión. */
    data object SesionInvalida : ResultadoRemoto<Nothing>

    /** El servicio falló al procesar una solicitud válida. */
    data class ErrorServidor(val codigo: String) : ResultadoRemoto<Nothing>

    /** Cualquier otra situación (dato rechazado, error inesperado). */
    data class ErrorDesconocido(val descripcion: String) : ResultadoRemoto<Nothing>
}

/**
 * ÚNICO punto de interpretación de los códigos de Firestore.
 * Recibe el NOMBRE del código (`FirebaseFirestoreException.Code.name`) para poder
 * probarlo en la JVM sin el SDK de Firebase.
 */
fun resultadoDesdeCodigoFirestore(codigo: String): ResultadoRemoto<Nothing> = when (codigo) {
    "UNAVAILABLE" -> ResultadoRemoto.SinConexion
    "DEADLINE_EXCEEDED" -> ResultadoRemoto.Timeout
    "PERMISSION_DENIED" -> ResultadoRemoto.SinPermiso
    "UNAUTHENTICATED" -> ResultadoRemoto.SesionInvalida
    "INTERNAL", "UNKNOWN", "ABORTED", "RESOURCE_EXHAUSTED", "DATA_LOSS" -> ResultadoRemoto.ErrorServidor(codigo)
    else -> ResultadoRemoto.ErrorDesconocido("Firestore rechazó la operación ($codigo)")
}

/**
 * Ejecuta una operación remota con límite de tiempo y la traduce a [ResultadoRemoto].
 * Las tareas de Firestore pueden no completarse nunca sin red: por eso el `withTimeout`.
 * [codigoDe] extrae el nombre del código del proveedor (o null si la excepción no es suya).
 * La cancelación de corrutinas nunca se convierte en error.
 */
suspend fun <T> ejecutarRemoto(
    timeoutMs: Long,
    codigoDe: (Throwable) -> String?,
    operacion: suspend () -> T
): ResultadoRemoto<T> =
    try {
        ResultadoRemoto.Exito(withTimeout(timeoutMs) { operacion() })
    } catch (e: TimeoutCancellationException) {
        ResultadoRemoto.Timeout
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val codigo = codigoDe(e)
        when {
            codigo != null -> resultadoDesdeCodigoFirestore(codigo)
            e is IOException -> ResultadoRemoto.SinConexion
            else -> ResultadoRemoto.ErrorDesconocido(e::class.java.simpleName)
        }
    }

/** Errores transitorios: conviene cortar la sincronización y reintentar más tarde. */
val ResultadoRemoto<*>.esTransitorio: Boolean
    get() = this is ResultadoRemoto.SinConexion || this is ResultadoRemoto.Timeout ||
        this is ResultadoRemoto.ErrorServidor

/** Texto para la persona usuaria: sin códigos técnicos ni nombres de excepciones. */
fun ResultadoRemoto<*>.mensajeParaUsuario(): String = when (this) {
    is ResultadoRemoto.Exito -> "Sincronizado"
    ResultadoRemoto.SinConexion -> "Sin conexión con la nube. Los cambios se enviarán al recuperar la red."
    ResultadoRemoto.Timeout -> "La nube tardó demasiado en responder. Se reintentará."
    ResultadoRemoto.SinPermiso -> "Acceso denegado"
    ResultadoRemoto.SesionInvalida -> "La sesión ya no es válida. Iniciá sesión nuevamente."
    is ResultadoRemoto.ErrorServidor -> "La nube tuvo un problema. Los cambios quedan guardados; reintentá más tarde."
    is ResultadoRemoto.ErrorDesconocido -> "No se pudo sincronizar por un error inesperado. Reintentá más tarde."
}
