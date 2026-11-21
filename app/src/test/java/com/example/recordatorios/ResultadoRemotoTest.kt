package com.example.recordatorios

import com.example.recordatorios.data.remote.ResultadoRemoto
import com.example.recordatorios.data.remote.llamarApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Interpretación centralizada de códigos HTTP y excepciones. */
class ResultadoRemotoTest {

    private fun cuerpoError(texto: String) = texto.toResponseBody("application/json".toMediaType())

    @Test
    fun codigo200EsExitoConDatos() = runTest {
        val resultado = llamarApi { Response.success(200, "ok") }
        assertEquals(ResultadoRemoto.Exito("ok"), resultado)
    }

    @Test
    fun codigo201y204SonExito() = runTest {
        assertEquals(ResultadoRemoto.Exito("creado"), llamarApi { Response.success(201, "creado") })
        assertEquals(ResultadoRemoto.Exito(null), llamarApi { Response.success<Unit>(204, null) })
    }

    @Test
    fun codigo400EsSolicitudInvalida() = runTest {
        val resultado = llamarApi { Response.error<String>(400, cuerpoError("""{"error":"titulo vacío"}""")) }
        assertTrue(resultado is ResultadoRemoto.SolicitudInvalida)
        resultado as ResultadoRemoto.SolicitudInvalida
        assertEquals(400, resultado.codigo)
        assertEquals("""{"error":"titulo vacío"}""", resultado.detalle)
    }

    @Test
    fun codigo404TambienEsSolicitudInvalida() = runTest {
        val resultado = llamarApi { Response.error<Unit>(404, cuerpoError("")) }
        assertEquals(ResultadoRemoto.SolicitudInvalida(404, null), resultado)
    }

    @Test
    fun codigo500EsErrorDelServidor() = runTest {
        assertEquals(ResultadoRemoto.ErrorServidor(500), llamarApi { Response.error<String>(500, cuerpoError("{}")) })
        assertEquals(ResultadoRemoto.ErrorServidor(503), llamarApi { Response.error<String>(503, cuerpoError("{}")) })
    }

    @Test
    fun socketTimeoutEsTimeout() = runTest {
        assertEquals(ResultadoRemoto.Timeout, llamarApi<String> { throw SocketTimeoutException("timeout") })
    }

    @Test
    fun faltaDeConexionEsSinConexion() = runTest {
        assertEquals(ResultadoRemoto.SinConexion, llamarApi<String> { throw UnknownHostException("host") })
        assertEquals(ResultadoRemoto.SinConexion, llamarApi<String> { throw ConnectException("refused") })
    }

    @Test
    fun respuestaIlegibleEsErrorDesconocido() = runTest {
        val resultado = llamarApi<String> { throw SerializationException("json roto") }
        assertTrue(resultado is ResultadoRemoto.ErrorDesconocido)
    }

    @Test
    fun laCancelacionNoSeConvierteEnError() = runTest {
        val relanzada = try {
            llamarApi<String> { throw CancellationException("cancelada") }
            false
        } catch (_: CancellationException) {
            true
        }
        assertTrue("La cancelación debe propagarse, no convertirse en ResultadoRemoto", relanzada)
    }
}
