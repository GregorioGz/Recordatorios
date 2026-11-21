package com.example.recordatorios.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.data.SimuladorDeEscenarios
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del listado.
 * - Depende de INTERFACES (RecordatoriosRepository, SimuladorDeEscenarios), no de una lista concreta.
 * - Estado interno modificable: MutableStateFlow (privado).
 * - Estado expuesto a la UI: StateFlow (solo lectura).
 * - No guarda referencias a Activity, View ni componentes visuales.
 */
class ListaRecordatoriosViewModel(
    private val repositorio: RecordatoriosRepository,
    private val simulador: SimuladorDeEscenarios
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListaRecordatoriosUiState>(ListaRecordatoriosUiState.Cargando)
    val uiState: StateFlow<ListaRecordatoriosUiState> = _uiState.asStateFlow()

    private var cargaEnCurso: Job? = null

    init {
        cargar()
    }

    /** Carga asincrónica con viewModelScope: la UI sigue respondiendo mientras tanto. */
    fun cargar() {
        cargaEnCurso?.cancel()
        cargaEnCurso = viewModelScope.launch {
            _uiState.update { ListaRecordatoriosUiState.Cargando }
            val nuevoEstado = try {
                val lista = repositorio.obtenerTodos()
                if (lista.isEmpty()) ListaRecordatoriosUiState.Vacio
                else ListaRecordatoriosUiState.ConDatos(lista)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ListaRecordatoriosUiState.Error(e.message ?: "Ocurrió un error inesperado.")
            }
            _uiState.update { nuevoEstado }
        }
    }

    // ---------- Eventos que envía la interfaz ----------
    fun onReintentar() = cargar()

    fun onSimular(simulacion: Simulacion) {
        when (simulacion) {
            Simulacion.LISTA_VACIA -> {
                simulador.vaciarDatos(); cargar()
            }
            Simulacion.ERROR_DE_CARGA -> {
                simulador.simularErrorEnProximaCarga(); cargar()
            }
            Simulacion.ERROR_AL_GUARDAR -> simulador.simularErrorEnProximoGuardado()
            Simulacion.RESTAURAR -> {
                simulador.restaurarDatosDeEjemplo(); cargar()
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ListaRecordatoriosViewModel(AppContainer.repositorio, AppContainer.simulador)
            }
        }
    }
}
