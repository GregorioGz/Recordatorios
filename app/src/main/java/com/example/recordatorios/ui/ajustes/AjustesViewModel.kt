package com.example.recordatorios.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.preferencias.FiltroPrioridad
import com.example.recordatorios.data.preferencias.Orden
import com.example.recordatorios.data.preferencias.PreferenciasRepository
import com.example.recordatorios.data.preferencias.Tema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lee las preferencias como Flow y las escribe con funciones suspend
 * lanzadas en viewModelScope (nunca bloquea el hilo principal).
 */
class AjustesViewModel(
    private val repositorio: PreferenciasRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AjustesUiState())
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repositorio.preferencias.collect { preferencias ->
                _uiState.update { it.copy(preferencias = preferencias, cargando = false) }
            }
        }
    }

    fun onTemaSeleccionado(tema: Tema) = guardar { repositorio.cambiarTema(tema) }
    fun onMostrarCompletadosCambiado(mostrar: Boolean) = guardar { repositorio.cambiarMostrarCompletados(mostrar) }
    fun onOrdenSeleccionado(orden: Orden) = guardar { repositorio.cambiarOrden(orden) }
    fun onFiltroSeleccionado(filtro: FiltroPrioridad) = guardar { repositorio.cambiarFiltroPrioridad(filtro) }

    fun onErrorDescartado() = _uiState.update { it.copy(mensajeError = null) }

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
            initializer { AjustesViewModel(AppContainer.preferencias) }
        }
    }
}
