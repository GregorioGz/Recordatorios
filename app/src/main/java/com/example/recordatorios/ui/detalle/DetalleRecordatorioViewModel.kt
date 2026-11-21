package com.example.recordatorios.ui.detalle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.navigation.Rutas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Recibe SOLO el identificador del recordatorio (argumento de navegación,
 * leído desde SavedStateHandle) y OBSERVA el registro en Room: si cambia
 * (edición, cambio de estado, sincronización) la pantalla se actualiza sola.
 */
class DetalleRecordatorioViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: RecordatoriosRepository
) : ViewModel() {

    private val recordatorioId: String = checkNotNull(savedStateHandle.get<String>(Rutas.ARG_RECORDATORIO_ID)) {
        "Falta el argumento ${Rutas.ARG_RECORDATORIO_ID}"
    }

    private val _uiState = MutableStateFlow<DetalleRecordatorioUiState>(DetalleRecordatorioUiState.Cargando)
    val uiState: StateFlow<DetalleRecordatorioUiState> = _uiState.asStateFlow()

    private var observacion: Job? = null

    /** true después de confirmar la eliminación: el próximo "null" de Room significa "eliminado". */
    private var eliminacionSolicitada = false

    init {
        observar()
    }

    private fun observar() {
        observacion?.cancel()
        observacion = viewModelScope.launch {
            repositorio.observarRecordatorio(recordatorioId)
                .catch { _uiState.value = DetalleRecordatorioUiState.Error("No se pudo leer el recordatorio guardado.") }
                .collect { recordatorio ->
                    _uiState.update { actual ->
                        when {
                            recordatorio != null ->
                                // Conserva el diálogo y el mensaje si ya estaban visibles
                                (actual as? DetalleRecordatorioUiState.Encontrado)?.copy(recordatorio = recordatorio)
                                    ?: DetalleRecordatorioUiState.Encontrado(recordatorio)
                            eliminacionSolicitada -> DetalleRecordatorioUiState.Eliminado
                            else -> DetalleRecordatorioUiState.NoEncontrado(recordatorioId)
                        }
                    }
                }
        }
    }

    // ---------- Eventos de la interfaz ----------
    fun onReintentar() {
        _uiState.value = DetalleRecordatorioUiState.Cargando
        observar()
    }

    fun onEstadoSeleccionado(estado: EstadoRecordatorio) {
        viewModelScope.launch {
            try {
                repositorio.cambiarEstado(recordatorioId, estado)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarMensaje("No se pudo cambiar el estado. Intentá nuevamente.")
            }
        }
    }

    fun onEliminarClick() = actualizarEncontrado { it.copy(confirmandoEliminacion = true) }

    fun onCancelarEliminacion() = actualizarEncontrado { it.copy(confirmandoEliminacion = false) }

    fun onConfirmarEliminacion() {
        actualizarEncontrado { it.copy(confirmandoEliminacion = false) }
        eliminacionSolicitada = true
        viewModelScope.launch {
            try {
                // Room emite null al ocultarse/borrarse el registro -> estado Eliminado
                repositorio.eliminar(recordatorioId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eliminacionSolicitada = false
                mostrarMensaje("No se pudo eliminar el recordatorio. Intentá nuevamente.")
            }
        }
    }

    fun onReintentarSincronizacion() = repositorio.sincronizarEnSegundoPlano()

    fun onMensajeMostrado() = actualizarEncontrado { it.copy(mensaje = null) }

    private fun mostrarMensaje(mensaje: String) = actualizarEncontrado { it.copy(mensaje = mensaje) }

    private fun actualizarEncontrado(
        cambio: (DetalleRecordatorioUiState.Encontrado) -> DetalleRecordatorioUiState.Encontrado
    ) = _uiState.update { if (it is DetalleRecordatorioUiState.Encontrado) cambio(it) else it }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DetalleRecordatorioViewModel(createSavedStateHandle(), AppContainer.repositorio)
            }
        }
    }
}
