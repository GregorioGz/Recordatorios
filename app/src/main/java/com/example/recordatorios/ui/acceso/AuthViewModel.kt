package com.example.recordatorios.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.auth.AuthRepository
import com.example.recordatorios.data.auth.ResultadoAuth
import com.example.recordatorios.model.EstadoSesion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de acceso: valida los campos y llama al [AuthRepository].
 * No conoce Firebase: recibe [ResultadoAuth] con errores ya traducidos.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sesion.collect { sesion ->
                when (sesion) {
                    // Si se llegó acá por una sesión inválida, se muestra el motivo
                    is EstadoSesion.Invalida -> _uiState.update { it.copy(avisoSesion = sesion.motivo) }
                    // Con la sesión iniciada, el formulario queda limpio para la próxima persona
                    is EstadoSesion.Activa -> _uiState.value = AuthUiState()
                    else -> Unit
                }
            }
        }
    }

    fun onEmailCambiado(valor: String) = _uiState.update { it.copy(email = valor, errorEmail = null, envio = EnvioAuth.Inactivo) }
    fun onContrasenaCambiada(valor: String) =
        _uiState.update { it.copy(contrasena = valor, errorContrasena = null, envio = EnvioAuth.Inactivo) }
    fun onConfirmacionCambiada(valor: String) =
        _uiState.update { it.copy(confirmacion = valor, errorConfirmacion = null, envio = EnvioAuth.Inactivo) }

    fun onAlternarModo() = _uiState.update {
        AuthUiState(
            modo = if (it.modo == ModoAcceso.INICIAR_SESION) ModoAcceso.REGISTRO else ModoAcceso.INICIAR_SESION,
            email = it.email,
            avisoSesion = it.avisoSesion
        )
    }

    fun onEnviar() {
        val estado = _uiState.value
        if (estado.cargando) return   // evita envíos dobles
        val conErrores = validar(estado)
        if (conErrores.errorEmail != null || conErrores.errorContrasena != null || conErrores.errorConfirmacion != null) {
            _uiState.value = conErrores
            return
        }
        _uiState.update { it.copy(envio = EnvioAuth.Cargando, avisoSesion = null) }
        viewModelScope.launch {
            val resultado = if (estado.modo == ModoAcceso.REGISTRO) {
                authRepository.registrar(estado.email, estado.contrasena)
            } else {
                authRepository.iniciarSesion(estado.email, estado.contrasena)
            }
            // La contraseña se descarta en ambos casos
            _uiState.update {
                it.copy(
                    contrasena = "",
                    confirmacion = "",
                    envio = when (resultado) {
                        is ResultadoAuth.Exito -> EnvioAuth.Exito
                        is ResultadoAuth.Error -> EnvioAuth.Error(resultado.error.mensaje)
                    }
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(AppContainer.auth) }
        }
    }
}

/** Validación local antes de llamar al proveedor (función pura). */
internal fun validar(estado: AuthUiState): AuthUiState {
    val email = estado.email.trim()
    val errorEmail = when {
        email.isEmpty() -> "Ingresá tu email"
        !FORMATO_EMAIL.matches(email) -> "El email no tiene un formato válido"
        else -> null
    }
    val errorContrasena = when {
        estado.contrasena.isEmpty() -> "Ingresá la contraseña"
        estado.modo == ModoAcceso.REGISTRO && estado.contrasena.length < MIN_CONTRASENA ->
            "La contraseña debe tener al menos $MIN_CONTRASENA caracteres"
        else -> null
    }
    val errorConfirmacion =
        if (estado.modo == ModoAcceso.REGISTRO && estado.confirmacion != estado.contrasena) {
            "Las contraseñas no coinciden"
        } else null
    return estado.copy(errorEmail = errorEmail, errorContrasena = errorContrasena, errorConfirmacion = errorConfirmacion)
}

private const val MIN_CONTRASENA = 6
private val FORMATO_EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
