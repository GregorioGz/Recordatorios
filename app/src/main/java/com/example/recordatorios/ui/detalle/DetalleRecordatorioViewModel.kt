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
import com.example.recordatorios.navigation.Rutas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Recibe SOLO el identificador del recordatorio (argumento de navegación,
 * leído desde SavedStateHandle) y busca el registro completo en el repositorio.
 */
class DetalleRecordatorioViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: RecordatoriosRepository
) : ViewModel() {

    private val recordatorioId: Int = checkNotNull(savedStateHandle.get<Int>(Rutas.ARG_RECORDATORIO_ID)) {
        "Falta el argumento ${Rutas.ARG_RECORDATORIO_ID}"
    }

    private val _uiState = MutableStateFlow<DetalleRecordatorioUiState>(DetalleRecordatorioUiState.Cargando)
    val uiState: StateFlow<DetalleRecordatorioUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _uiState.update { DetalleRecordatorioUiState.Cargando }
            val nuevoEstado = try {
                val recordatorio = repositorio.obtenerPorId(recordatorioId)
                if (recordatorio != null) DetalleRecordatorioUiState.Encontrado(recordatorio)
                else DetalleRecordatorioUiState.NoEncontrado(recordatorioId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetalleRecordatorioUiState.Error(e.message ?: "No se pudo cargar el recordatorio.")
            }
            _uiState.update { nuevoEstado }
        }
    }

    fun onReintentar() = cargar()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DetalleRecordatorioViewModel(createSavedStateHandle(), AppContainer.repositorio)
            }
        }
    }
}
