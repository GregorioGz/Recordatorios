package com.example.recordatorios.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.data.SimuladorDeEscenarios
import com.example.recordatorios.data.preferencias.FiltroPrioridad
import com.example.recordatorios.data.preferencias.Orden
import com.example.recordatorios.data.preferencias.PreferenciasRepository
import com.example.recordatorios.data.preferencias.PreferenciasUsuario
import com.example.recordatorios.data.remote.EscenarioRemoto
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del listado.
 * - Observa Room a través del repositorio (Flow): cualquier alta, edición,
 *   eliminación o sincronización se refleja sola, sin recargas manuales.
 * - Combina los datos con las preferencias de DataStore (orden, filtro, completados).
 * - Expone el estado global de sincronización para el banner.
 * - Estado interno modificable: MutableStateFlow (privado). Expuesto: StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListaRecordatoriosViewModel(
    private val repositorio: RecordatoriosRepository,
    private val preferenciasRepository: PreferenciasRepository,
    private val simulador: SimuladorDeEscenarios
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListaRecordatoriosUiState>(ListaRecordatoriosUiState.Cargando)
    val uiState: StateFlow<ListaRecordatoriosUiState> = _uiState.asStateFlow()

    private val _sincronizacion = MutableStateFlow(SincronizacionUiState())
    val sincronizacion: StateFlow<SincronizacionUiState> = _sincronizacion.asStateFlow()

    /** Cada incremento vuelve a suscribirse a Room (botón Reintentar). */
    private val reintentos = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(reintentos, preferenciasRepository.preferencias) { _, preferencias -> preferencias }
                .flatMapLatest { preferencias ->
                    repositorio.observarRecordatorios(preferencias.filtroPrioridad.aPrioridad())
                        .map { lista -> estadoPara(lista, preferencias) }
                        .catch {
                            emit(
                                ListaRecordatoriosUiState.Error(
                                    "No se pudieron leer los recordatorios guardados en el dispositivo."
                                )
                            )
                        }
                }
                .collect { nuevoEstado -> _uiState.value = nuevoEstado }
        }
        viewModelScope.launch {
            combine(repositorio.estadoSincronizacion, simulador.escenarioRemoto) { estado, escenario ->
                estado to escenario?.aSimulacion()
            }.collect { (estado, escenario) ->
                _sincronizacion.update { it.copy(estado = estado, escenarioServidorActivo = escenario) }
            }
        }
    }

    // ---------- Eventos que envía la interfaz ----------
    fun onReintentar() {
        _uiState.value = ListaRecordatoriosUiState.Cargando
        reintentos.update { it + 1 }
    }

    fun onSincronizarAhora() = repositorio.sincronizarEnSegundoPlano()

    fun onMensajeMostrado() = _sincronizacion.update { it.copy(mensaje = null) }

    fun onSimular(simulacion: Simulacion) {
        when (simulacion) {
            Simulacion.LISTA_VACIA -> simulador.simularListaVacia()
            Simulacion.ERROR_DE_CARGA -> {
                simulador.simularErrorEnProximaLectura(); onReintentar()
            }
            Simulacion.ERROR_AL_GUARDAR -> {
                simulador.simularErrorEnProximoGuardado()
                mostrar("El próximo guardado (crear o editar) fallará una vez.")
            }
            Simulacion.RESTAURAR -> viewModelScope.launch {
                simulador.restaurarDatosDeEjemplo()
                mostrar("Simulaciones locales desactivadas y ejemplos restaurados.")
            }
            Simulacion.TEMA_INVALIDO -> viewModelScope.launch {
                preferenciasRepository.guardarTemaInvalidoParaPruebas()
                mostrar("Se guardó un tema inválido: la app usa el predeterminado (según el sistema).")
            }
            Simulacion.SINCRONIZACION_CONCURRENTE -> viewModelScope.launch {
                mostrar(simulador.probarSincronizacionConcurrente(cantidad = 3))
            }
            else -> {
                val escenario = simulacion.aEscenarioRemoto() ?: return
                if (simulador.cambiarEscenarioRemoto(escenario)) {
                    mostrar("${simulacion.etiqueta}. Creá, editá o sincronizá para probarlo.")
                } else {
                    mostrar("Hay una URL real configurada: el servidor simulado no está activo.")
                }
            }
        }
    }

    private fun mostrar(mensaje: String) = _sincronizacion.update { it.copy(mensaje = mensaje) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ListaRecordatoriosViewModel(
                    AppContainer.repositorio,
                    AppContainer.preferencias,
                    AppContainer.simulador
                )
            }
        }
    }
}

private fun Simulacion.aEscenarioRemoto(): EscenarioRemoto? = when (this) {
    Simulacion.SERVIDOR_NORMAL -> EscenarioRemoto.NORMAL
    Simulacion.SERVIDOR_ERROR_500 -> EscenarioRemoto.ERROR_SERVIDOR
    Simulacion.SERVIDOR_ERROR_400 -> EscenarioRemoto.SOLICITUD_INVALIDA
    Simulacion.SERVIDOR_TIMEOUT -> EscenarioRemoto.TIMEOUT
    Simulacion.SERVIDOR_SIN_CONEXION -> EscenarioRemoto.SIN_CONEXION
    else -> null
}

private fun EscenarioRemoto.aSimulacion(): Simulacion = when (this) {
    EscenarioRemoto.NORMAL -> Simulacion.SERVIDOR_NORMAL
    EscenarioRemoto.ERROR_SERVIDOR -> Simulacion.SERVIDOR_ERROR_500
    EscenarioRemoto.SOLICITUD_INVALIDA -> Simulacion.SERVIDOR_ERROR_400
    EscenarioRemoto.TIMEOUT -> Simulacion.SERVIDOR_TIMEOUT
    EscenarioRemoto.SIN_CONEXION -> Simulacion.SERVIDOR_SIN_CONEXION
}

private fun FiltroPrioridad.aPrioridad(): Prioridad? = when (this) {
    FiltroPrioridad.TODAS -> null
    FiltroPrioridad.ALTA -> Prioridad.Alta
    FiltroPrioridad.MEDIA -> Prioridad.Media
    FiltroPrioridad.BAJA -> Prioridad.Baja
}

private fun estadoPara(lista: List<Recordatorio>, preferencias: PreferenciasUsuario): ListaRecordatoriosUiState {
    val visibles = lista.aplicarPreferencias(preferencias)
    val hayAjustesQueOcultan = preferencias.filtroPrioridad != FiltroPrioridad.TODAS || !preferencias.mostrarCompletados
    return when {
        visibles.isNotEmpty() -> ListaRecordatoriosUiState.ConDatos(visibles)
        lista.isNotEmpty() || hayAjustesQueOcultan -> ListaRecordatoriosUiState.Vacio(ocultosPorAjustes = true)
        else -> ListaRecordatoriosUiState.Vacio()
    }
}

/** Función pura: completados -> filtro de prioridad -> orden. */
internal fun List<Recordatorio>.aplicarPreferencias(preferencias: PreferenciasUsuario): List<Recordatorio> {
    val prioridadFiltrada = preferencias.filtroPrioridad.aPrioridad()
    val filtrados = filter { recordatorio ->
        (preferencias.mostrarCompletados || recordatorio.estado != EstadoRecordatorio.Completado) &&
            (prioridadFiltrada == null || recordatorio.prioridad == prioridadFiltrada)
    }
    return when (preferencias.orden) {
        Orden.FECHA -> filtrados.sortedByDescending { it.fechaCreacion }
        Orden.PRIORIDAD -> filtrados.sortedWith(
            compareByDescending<Recordatorio> { it.prioridad.nivel }.thenBy { it.titulo.lowercase() }
        )
        Orden.TITULO -> filtrados.sortedBy { it.titulo.lowercase() }
    }
}
