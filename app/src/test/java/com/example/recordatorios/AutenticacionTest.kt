package com.example.recordatorios

import com.example.recordatorios.data.auth.ErrorAuth
import com.example.recordatorios.ui.acceso.AuthUiState
import com.example.recordatorios.ui.acceso.ModoAcceso
import com.example.recordatorios.ui.acceso.validar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Traducción de errores de Firebase Authentication y validación del formulario de acceso. */
class AutenticacionTest {

    @Test
    fun codigosDeFirebaseAuthSeTraducenAErroresPropios() {
        assertEquals(ErrorAuth.CREDENCIALES_INVALIDAS, ErrorAuth.desdeCodigo("ERROR_INVALID_CREDENTIAL"))
        assertEquals(ErrorAuth.CREDENCIALES_INVALIDAS, ErrorAuth.desdeCodigo("ERROR_WRONG_PASSWORD"))
        assertEquals(ErrorAuth.USUARIO_INEXISTENTE, ErrorAuth.desdeCodigo("ERROR_USER_NOT_FOUND"))
        assertEquals(ErrorAuth.EMAIL_YA_REGISTRADO, ErrorAuth.desdeCodigo("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(ErrorAuth.CONTRASENA_DEBIL, ErrorAuth.desdeCodigo("ERROR_WEAK_PASSWORD"))
        assertEquals(ErrorAuth.EMAIL_MAL_FORMADO, ErrorAuth.desdeCodigo("ERROR_INVALID_EMAIL"))
        assertEquals(ErrorAuth.SIN_CONEXION, ErrorAuth.desdeCodigo("ERROR_NETWORK_REQUEST_FAILED"))
        assertEquals(ErrorAuth.DEMASIADOS_INTENTOS, ErrorAuth.desdeCodigo("ERROR_TOO_MANY_REQUESTS"))
        assertEquals(ErrorAuth.DESCONOCIDO, ErrorAuth.desdeCodigo("ERROR_ALGO_NUEVO"))
        assertEquals(ErrorAuth.DESCONOCIDO, ErrorAuth.desdeCodigo(null))
    }

    @Test
    fun conLaSesionAbiertaUsuarioInexistenteSignificaCuentaEliminada() {
        assertEquals(ErrorAuth.CUENTA_ELIMINADA, ErrorAuth.desdeCodigo("ERROR_USER_NOT_FOUND", enSesionAbierta = true))
    }

    @Test
    fun soloCuentaDeshabilitadaEliminadaOTokenRevocadoInvalidanLaSesion() {
        listOf("ERROR_USER_DISABLED", "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN").forEach {
            assertTrue(it, ErrorAuth.desdeCodigo(it, enSesionAbierta = true).invalidaLaSesion)
        }
        assertTrue(ErrorAuth.desdeCodigo("ERROR_USER_NOT_FOUND", enSesionAbierta = true).invalidaLaSesion)
        // Sin red no se pierde la sesión (la app sigue funcionando offline)
        assertFalse(ErrorAuth.desdeCodigo("ERROR_NETWORK_REQUEST_FAILED", enSesionAbierta = true).invalidaLaSesion)
    }

    @Test
    fun losMensajesNoExponenCodigosTecnicos() {
        ErrorAuth.entries.forEach { error ->
            assertFalse(error.mensaje, error.mensaje.contains("ERROR_"))
            assertFalse(error.mensaje, error.mensaje.contains("Firebase"))
        }
    }

    @Test
    fun validacionDelFormularioDeAcceso() {
        val sinDatos = validar(AuthUiState())
        assertNotNull(sinDatos.errorEmail)
        assertNotNull(sinDatos.errorContrasena)

        assertNotNull(validar(AuthUiState(email = "ana@", contrasena = "secreta1")).errorEmail)

        val loginValido = validar(AuthUiState(email = " ana@ejemplo.com ", contrasena = "x"))
        assertNull(loginValido.errorEmail)
        assertNull(loginValido.errorContrasena)   // en el inicio de sesión no se exige largo mínimo

        val registroCorto = validar(AuthUiState(modo = ModoAcceso.REGISTRO, email = "ana@ejemplo.com", contrasena = "123", confirmacion = "123"))
        assertNotNull(registroCorto.errorContrasena)

        val noCoinciden = validar(AuthUiState(modo = ModoAcceso.REGISTRO, email = "ana@ejemplo.com", contrasena = "secreta1", confirmacion = "secreta2"))
        assertNotNull(noCoinciden.errorConfirmacion)
    }
}
