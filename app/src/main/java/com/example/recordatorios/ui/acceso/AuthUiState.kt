package com.example.recordatorios.ui.acceso

/** La misma pantalla sirve para iniciar sesión o crear una cuenta. */
enum class ModoAcceso(val titulo: String, val textoBoton: String, val textoAlternar: String) {
    INICIAR_SESION("Iniciar sesión", "Ingresar", "¿No tenés cuenta? Registrate"),
    REGISTRO("Crear cuenta", "Registrarme", "¿Ya tenés cuenta? Iniciá sesión")
}

/** Estado del envío del formulario (mutuamente excluyentes). */
sealed interface EnvioAuth {
    data object Inactivo : EnvioAuth
    data object Cargando : EnvioAuth
    data class Error(val mensaje: String) : EnvioAuth

    /** Éxito: la navegación cambia sola al observar la sesión activa. */
    data object Exito : EnvioAuth
}

/**
 * Estado de la pantalla de acceso.
 * La contraseña vive solo en memoria mientras se escribe: no se guarda en disco
 * ni en SavedStateHandle, no se loguea y se borra después de cada envío.
 */
data class AuthUiState(
    val modo: ModoAcceso = ModoAcceso.INICIAR_SESION,
    val email: String = "",
    val contrasena: String = "",
    val confirmacion: String = "",
    val errorEmail: String? = null,
    val errorContrasena: String? = null,
    val errorConfirmacion: String? = null,
    val envio: EnvioAuth = EnvioAuth.Inactivo,
    /** Motivo por el que se cerró la sesión anterior (sesión inválida), si lo hay. */
    val avisoSesion: String? = null
) {
    val cargando: Boolean get() = envio is EnvioAuth.Cargando
}
