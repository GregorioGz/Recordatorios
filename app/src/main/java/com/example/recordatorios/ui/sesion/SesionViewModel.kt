package com.example.recordatorios.ui.sesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.auth.AuthRepository
import com.example.recordatorios.model.EstadoSesion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Qué parte de la app se muestra según la sesión (decisión de navegación de nivel superior). */
sealed interface DestinoSesion {
    /** Todavía se está leyendo la sesión guardada. */
    data object Verificando : DestinoSesion

    /** Sin sesión (o sesión inválida): pantalla de acceso. */
    data object Acceso : DestinoSesion

    /** Sesión activa: grafo principal. El uid separa el grafo de cada cuenta. */
    data class App(val uid: String) : DestinoSesion
}

class SesionViewModel(authRepository: AuthRepository) : ViewModel() {

    val destino: StateFlow<DestinoSesion> = authRepository.sesion
        .map { sesion ->
            when (sesion) {
                EstadoSesion.Verificando -> DestinoSesion.Verificando
                EstadoSesion.SinSesion, is EstadoSesion.Invalida -> DestinoSesion.Acceso
                is EstadoSesion.Activa -> DestinoSesion.App(sesion.usuario.uid)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DestinoSesion.Verificando)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SesionViewModel(AppContainer.auth) }
        }
    }
}
