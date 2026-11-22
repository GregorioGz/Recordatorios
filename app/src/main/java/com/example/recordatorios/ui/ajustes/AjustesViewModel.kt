package com.example.recordatorios.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.BuildConfig
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.data.ResultadoCierreSesion
import com.example.recordatorios.data.auth.AuthRepository
import com.example.recordatorios.data.preferencias.FiltroPrioridad
import com.example.recordatorios.data.preferencias.Orden
import com.example.recordatorios.data.preferencias.PreferenciasRepository
import com.example.recordatorios.data.preferencias.Tema
import com.example.recordatorios.model.EstadoSesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lee las preferencias como Flow y las escribe con funciones suspend
 * lanzadas en viewModelScope (nunca bloquea el hilo principal).
 * También coordina el cierre de sesión (a través del repositorio, nunca de Firebase).
 */
class AjustesViewModel(
    private val repositorio: PreferenciasRepository,
    private val recordatorios: RecordatoriosRepository,
    private val auth: AuthRepository,
    private val mostrarUid: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(AjustesUiState())
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repositorio.preferencias.collect { preferencias ->
                _uiState.update { it.copy(preferencias = preferencias, cargando = false) }
            }
        }
        viewModelScope.launch {
            auth.sesion.collect { sesion ->
                val usuario = (sesion as? EstadoSesion.Activa)?.usuario
                _uiState.update {
                    it.copy(cuenta = usuario?.email, uidVisible = usuario?.uid?.takeIf { mostrarUid })
                }
            }
        }
    }

    fun onTemaSeleccionado(tema: Tema) = guardar { repositorio.cambiarTema(tema) }
    fun onMostrarCompletadosCambiado(mostrar: Boolean) = guardar { repositorio.cambiarMostrarCompletados(mostrar) }
    fun onOrdenSeleccionado(orden: Orden) = guardar { repositorio.cambiarOrden(orden) }
    fun onFiltroSeleccionado(filtro: FiltroPrioridad) = guardar { repositorio.cambiarFiltroPrioridad(filtro) }

    fun onErrorDescartado() = _uiState.update { it.copy(mensajeError = null) }

    // ---------- Cierre de sesión ----------
    /** Si hay cambios sin sincronizar se pide confirmación; si no, se sale directamente. */
    fun onCerrarSesion() {
        if (_uiState.value.cierreSesion != CierreSesionUiState.Inactivo) return
        viewModelScope.launch {
            val pendientes = recordatorios.cantidadPendientes()
            if (pendientes > 0) {
                _uiState.update { it.copy(cierreSesion = CierreSesionUiState.Confirmando(pendientes)) }
            } else {
                cerrar(sincronizarAntes = false)
            }
        }
    }

    fun onSincronizarYSalir() = viewModelScope.launch { cerrar(sincronizarAntes = true) }
    fun onSalirIgual() = viewModelScope.launch { cerrar(sincronizarAntes = false) }
    fun onCancelarCierre() = _uiState.update { it.copy(cierreSesion = CierreSesionUiState.Inactivo) }

    private suspend fun cerrar(sincronizarAntes: Boolean) {
        _uiState.update { it.copy(cierreSesion = CierreSesionUiState.Cerrando) }
        val resultado = try {
            recordatorios.cerrarSesion(sincronizarAntes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(cierreSesion = CierreSesionUiState.Inactivo, mensajeError = "No se pudo cerrar la sesión. Intentá nuevamente.")
            }
            return
        }
        when (resultado) {
            // La navegación vuelve sola a la pantalla de acceso al observar la sesión
            ResultadoCierreSesion.Cerrada -> _uiState.update { it.copy(cierreSesion = CierreSesionUiState.Inactivo) }
            is ResultadoCierreSesion.QuedanPendientes -> _uiState.update {
                it.copy(
                    cierreSesion = CierreSesionUiState.Confirmando(resultado.cantidad),
                    mensajeError = "No se pudieron sincronizar ${resultado.cantidad} cambios" +
                        (resultado.motivo?.let { motivo -> ": $motivo" } ?: ".")
                )
            }
        }
    }

    private fun guardar(accion: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                accion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(mensajeError = "No se pudo guardar el ajuste. Intentá nuevamente.") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AjustesViewModel(
                    repositorio = AppContainer.preferencias,
                    recordatorios = AppContainer.repositorio,
                    auth = AppContainer.auth,
                    mostrarUid = BuildConfig.HERRAMIENTAS_DE_PRUEBA
                )
            }
        }
    }
}
