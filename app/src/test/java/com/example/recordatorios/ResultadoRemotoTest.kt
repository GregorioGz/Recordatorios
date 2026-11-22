package com.example.recordatorios

import com.example.recordatorios.data.remote.ResultadoRemoto
import com.example.recordatorios.data.remote.ejecutarRemoto
import com.example.recordatorios.data.remote.esTransitorio
import com.example.recordatorios.data.remote.mensajeParaUsuario
import com.example.recordatorios.data.remote.resultadoDesdeCodigoFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Traducción centralizada de códigos de Firestore y excepciones a ResultadoRemoto. */
class ResultadoRemotoTest {

    /** Simula una FirebaseFirestoreException sin depender del SDK: lleva el nombre del código. */
    private class ErrorConCodigo(val codigo: String) : Exception(codigo)

    private val codigoDe: (Throwable) -> String? = { (it as? ErrorConCodigo)?.codigo }

    @Test
    fun codigosDeFirestoreSeTraducenAResultadosPropios() {
        assertEquals(ResultadoRemoto.SinConexion, resultadoDesdeCodigoFirestore("UNAVAILABLE"))
        assertEquals(ResultadoRemoto.Timeout, resultadoDesdeCodigoFirestore("DEADLINE_EXCEEDED"))
        assertEquals(ResultadoRemoto.SinPermiso, resultadoDesdeCodigoFirestore("PERMISSION_DENIED"))
        assertEquals(ResultadoRemoto.SesionInvalida, resultadoDesdeCodigoFirestore("UNAUTHENTICATED"))
        assertEquals(ResultadoRemoto.ErrorServidor("INTERNAL"), resultadoDesdeCodigoFirestore("INTERNAL"))
        assertTrue(resultadoDesdeCodigoFirestore("INVALID_ARGUMENT") is ResultadoRemoto.ErrorDesconocido)
    }

    @Test
    fun permisoDenegadoSeMuestraComoAccesoDenegado() {
        assertEquals("Acceso denegado", ResultadoRemoto.SinPermiso.mensajeParaUsuario())
    }

    @Test
    fun soloRedTimeoutYErrorDelServidorSonTransitorios() {
        assertTrue(ResultadoRemoto.SinConexion.esTransitorio)
        assertTrue(ResultadoRemoto.Timeout.esTransitorio)
        assertTrue(ResultadoRemoto.ErrorServidor("INTERNAL").esTransitorio)
        assertFalse(ResultadoRemoto.SinPermiso.esTransitorio)
        assertFalse(ResultadoRemoto.SesionInvalida.esTransitorio)
    }

    @Test
    fun operacionExitosaDevuelveLosDatos() = runTest {
        assertEquals(ResultadoRemoto.Exito(3), ejecutarRemoto(1_000, codigoDe) { 3 })
    }

    @Test
    fun excepcionConCodigoUsaElTraductorDeFirestore() = runTest {
        assertEquals(
            ResultadoRemoto.SinPermiso,
            ejecutarRemoto(1_000, codigoDe) { throw ErrorConCodigo("PERMISSION_DENIED") }
        )
    }

    @Test
    fun unaTareaQueNuncaTerminaSinRedEsTimeout() = runTest {
        // Sin red, una tarea de Firestore puede no completarse nunca: withTimeout la corta
        assertEquals(ResultadoRemoto.Timeout, ejecutarRemoto<Unit>(10_000, codigoDe) { awaitCancellation() })
    }

    @Test
    fun errorDeEntradaSalidaEsSinConexion() = runTest {
        assertEquals(ResultadoRemoto.SinConexion, ejecutarRemoto<Unit>(1_000, codigoDe) { throw IOException("red") })
    }

    @Test
    fun excepcionDesconocidaEsErrorDesconocido() = runTest {
        assertTrue(ejecutarRemoto<Unit>(1_000, codigoDe) { error("x") } is ResultadoRemoto.ErrorDesconocido)
    }

    @Test
    fun laCancelacionNoSeConvierteEnError() = runTest {
        val relanzada = try {
            ejecutarRemoto<Unit>(1_000, codigoDe) { throw CancellationException("cancelada") }
            false
        } catch (_: CancellationException) {
            true
        }
        assertTrue("La cancelación debe propagarse, no convertirse en ResultadoRemoto", relanzada)
    }
}
