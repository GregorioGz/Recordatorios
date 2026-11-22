package com.example.recordatorios.data.auth

import com.example.recordatorios.model.Usuario

/**
 * Errores de autenticación propios de la app, con el mensaje en español.
 * La interfaz solo ve este enum: nunca excepciones ni códigos de Firebase.
 */
enum class ErrorAuth(val mensaje: String, val invalidaLaSesion: Boolean = false) {
    CREDENCIALES_INVALIDAS("El email o la contraseña no son correctos."),
    USUARIO_INEXISTENTE("No existe una cuenta con ese email."),
    EMAIL_YA_REGISTRADO("Ya existe una cuenta con ese email. Iniciá sesión."),
    CONTRASENA_DEBIL("La contraseña es demasiado débil: usá al menos 6 caracteres."),
    EMAIL_MAL_FORMADO("El email no tiene un formato válido."),
    SIN_CONEXION("Sin conexión. Revisá la red e intentá nuevamente."),
    DEMASIADOS_INTENTOS("Demasiados intentos. Esperá unos minutos e intentá nuevamente."),
    CUENTA_DESHABILITADA("La cuenta fue deshabilitada. Contactá al administrador.", invalidaLaSesion = true),
    CUENTA_ELIMINADA("La cuenta ya no existe. Iniciá sesión con otra cuenta.", invalidaLaSesion = true),
    SESION_VENCIDA("Tu sesión venció o fue revocada. Iniciá sesión nuevamente.", invalidaLaSesion = true),
    DESCONOCIDO("No se pudo completar la operación. Intentá nuevamente.");

    companion object {
        /**
         * ÚNICO punto de traducción. Recibe el código de error del proveedor como texto
         * (por ejemplo "ERROR_INVALID_EMAIL"), para poder probarlo sin el SDK de Firebase.
         * [enSesionAbierta] distingue "usuario inexistente" al iniciar sesión
         * (credenciales) de "cuenta eliminada" al verificar una sesión ya abierta.
         */
        fun desdeCodigo(codigo: String?, enSesionAbierta: Boolean = false): ErrorAuth = when (codigo) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD" -> CREDENCIALES_INVALIDAS
            "ERROR_USER_NOT_FOUND" -> if (enSesionAbierta) CUENTA_ELIMINADA else USUARIO_INEXISTENTE
            "ERROR_EMAIL_ALREADY_IN_USE" -> EMAIL_YA_REGISTRADO
            "ERROR_WEAK_PASSWORD" -> CONTRASENA_DEBIL
            "ERROR_INVALID_EMAIL" -> EMAIL_MAL_FORMADO
            "ERROR_NETWORK_REQUEST_FAILED" -> SIN_CONEXION
            "ERROR_TOO_MANY_REQUESTS" -> DEMASIADOS_INTENTOS
            "ERROR_USER_DISABLED" -> CUENTA_DESHABILITADA
            "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN" -> SESION_VENCIDA
            else -> DESCONOCIDO
        }
    }
}

/** Resultado de registrar o iniciar sesión. */
sealed interface ResultadoAuth {
    data class Exito(val usuario: Usuario) : ResultadoAuth
    data class Error(val error: ErrorAuth) : ResultadoAuth
}
