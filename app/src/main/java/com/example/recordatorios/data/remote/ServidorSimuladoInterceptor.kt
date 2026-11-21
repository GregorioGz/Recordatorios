package com.example.recordatorios.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/** Escenarios que puede reproducir el servidor simulado (menú ⋮ de pruebas). */
enum class EscenarioRemoto {
    NORMAL,          // 200 / 201 / 204
    ERROR_SERVIDOR,  // 500
    SOLICITUD_INVALIDA, // 400
    TIMEOUT,         // SocketTimeoutException
    SIN_CONEXION     // UnknownHostException
}

/**
 * Interceptor de OkHttp que actúa como BACKEND EN MEMORIA cuando la URL base es
 * la de ejemplo. Responde JSON real con códigos HTTP reales, así Retrofit,
 * el conversor y la interpretación de errores funcionan igual que con un servidor externo.
 *
 * Endpoints:
 *   GET    /recordatorios        -> 200 + lista
 *   PUT    /recordatorios/{id}   -> 201 (creado) / 200 (actualizado) / 400 (inválido)
 *   DELETE /recordatorios/{id}   -> 204 (eliminado) / 404 (no existía)
 *
 * Nota: los datos viven en memoria, por lo que se pierden al cerrar la app.
 */
class ServidorSimuladoInterceptor(
    private val json: Json,
    private val latenciaMs: Long = 400,
    private val demoraTimeoutMs: Long = 1_500
) : Interceptor {

    private val registros = LinkedHashMap<String, RecordatorioDto>()
    private val putRecibidos = AtomicInteger(0)

    private val _escenario = MutableStateFlow(EscenarioRemoto.NORMAL)
    val escenario: StateFlow<EscenarioRemoto> = _escenario.asStateFlow()

    fun cambiarEscenario(nuevo: EscenarioRemoto) {
        _escenario.value = nuevo
    }

    /** Cantidad de registros guardados en el "servidor" (para comprobar que no hay duplicados). */
    fun cantidadDeRegistros(): Int = synchronized(registros) { registros.size }

    fun cantidadDePutRecibidos(): Int = putRecibidos.get()
    fun reiniciarContadorDePut() = putRecibidos.set(0)

    override fun intercept(chain: Interceptor.Chain): Response {
        val solicitud = chain.request()
        Thread.sleep(latenciaMs) // simula la demora de la red (corre en un hilo de OkHttp)

        return when (_escenario.value) {
            EscenarioRemoto.SIN_CONEXION ->
                throw UnknownHostException("Servidor simulado: sin conexión")
            EscenarioRemoto.TIMEOUT -> {
                Thread.sleep(demoraTimeoutMs)
                throw SocketTimeoutException("Servidor simulado: tiempo de espera agotado")
            }
            EscenarioRemoto.ERROR_SERVIDOR ->
                responder(solicitud, 500, error("Error interno del servidor simulado"))
            EscenarioRemoto.SOLICITUD_INVALIDA ->
                responder(solicitud, 400, error("Solicitud inválida (simulada)"))
            EscenarioRemoto.NORMAL -> atender(solicitud)
        }
    }

    private fun atender(solicitud: Request): Response {
        val segmentos = solicitud.url.pathSegments.filter { it.isNotEmpty() }
        if (segmentos.firstOrNull() != "recordatorios") {
            return responder(solicitud, 404, error("Recurso inexistente"))
        }
        val id = segmentos.getOrNull(1)

        return when {
            solicitud.method == "GET" && id == null -> {
                val lista = synchronized(registros) { registros.values.toList() }
                responder(solicitud, 200, json.encodeToString(lista))
            }

            solicitud.method == "PUT" && id != null -> {
                putRecibidos.incrementAndGet()
                val dto = try {
                    json.decodeFromString<RecordatorioDto>(leerCuerpo(solicitud))
                } catch (_: SerializationException) {
                    return responder(solicitud, 400, error("JSON inválido"))
                } catch (_: IllegalArgumentException) {
                    return responder(solicitud, 400, error("JSON inválido"))
                }
                if (dto.id != id || dto.titulo.isBlank()) {
                    return responder(solicitud, 400, error("El id del cuerpo no coincide o falta el título"))
                }
                // Upsert por id: el mismo PUT repetido no crea un segundo registro
                val existia = synchronized(registros) { registros.put(id, dto) != null }
                responder(solicitud, if (existia) 200 else 201, json.encodeToString(dto))
            }

            solicitud.method == "DELETE" && id != null -> {
                val existia = synchronized(registros) { registros.remove(id) != null }
                if (existia) responder(solicitud, 204, "")
                else responder(solicitud, 404, error("No existe el recordatorio $id"))
            }

            else -> responder(solicitud, 405, error("Método no permitido"))
        }
    }

    private fun leerCuerpo(solicitud: Request): String {
        val buffer = Buffer()
        solicitud.body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun error(mensaje: String) = json.encodeToString(ErrorDto(mensaje))

    private fun responder(solicitud: Request, codigo: Int, cuerpo: String): Response =
        Response.Builder()
            .request(solicitud)
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message(mensajeHttp(codigo))
            .body(cuerpo.toResponseBody(TIPO_JSON))
            .build()

    private fun mensajeHttp(codigo: Int) = when (codigo) {
        200 -> "OK"; 201 -> "Created"; 204 -> "No Content"
        400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        else -> "HTTP $codigo"
    }

    private companion object {
        val TIPO_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
