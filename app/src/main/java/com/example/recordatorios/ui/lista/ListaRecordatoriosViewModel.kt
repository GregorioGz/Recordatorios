package com.example.recordatorios.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.BuildConfig
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.data.SimuladorDeEscenarios
import com.example.recordatorios.data.preferencias.FiltroPrioridad
import com.example.recordatorios.data.preferencias.Orden
import com.example.recordatorios.data.preferencias.PreferenciasRepository
import com.example.recordatorios.data.preferencias.PreferenciasUsuario
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

    private val _sincronizacion = MutableStateFlow(SincronizacionUiState(herramientasDePrueba = BuildConfig.HERRAMIENTAS_DE_PRUEBA))
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
            repositorio.estadoSincronizacion.collect { estado ->
                _sincronizacion.update { it.copy(estado = estado) }
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
            Simulacion.CARGAR_EJEMPLOS -> viewModelScope.launch {
                val agregados = simulador.cargarDatosDeEjemplo()
                mostrar("Simulaciones desactivadas. Ejemplos agregados a tu cuenta: $agregados.")
            }
            Simulacion.TEMA_INVALIDO -> viewModelScope.launch {
                preferenciasRepository.guardarTemaInvalidoParaPruebas()
                mostrar("Se guardó un tema inválido: la app usa el predeterminado (según el sistema).")
            }
            Simulacion.SINCRONIZACION_CONCURRENTE -> viewModelScope.launch {
                val resumen = simulador.probarSincronizacionConcurrente(cantidad = 3)
                _sincronizacion.update { it.copy(resultadoPrueba = resumen) }
            }
            Simulacion.LEER_OTRA_CUENTA, Simulacion.ESCRIBIR_OTRA_CUENTA ->
                _sincronizacion.update { it.copy(pruebaOtraCuenta = PruebaOtraCuentaUiState(operacion = simulacion)) }
        }
    }

    // ---------- Prueba de acceso a otra cuenta (autorización) ----------
    fun onUidOtraCuentaCambiado(uid: String) = _sincronizacion.update {
        it.copy(pruebaOtraCuenta = it.pruebaOtraCuenta?.copy(uid = uid))
    }

    fun onCancelarPruebaOtraCuenta() = _sincronizacion.update { it.copy(pruebaOtraCuenta = null) }

    fun onEjecutarPruebaOtraCuenta() {
        val prueba = _sincronizacion.value.pruebaOtraCuenta ?: return
        if (prueba.ejecutando) return
        _sincronizacion.update { it.copy(pruebaOtraCuenta = prueba.copy(ejecutando = true)) }
        viewModelScope.launch {
            val resultado = if (prueba.operacion == Simulacion.ESCRIBIR_OTRA_CUENTA) {
                simulador.intentarEscribirEnOtraCuenta(prueba.uid)
            } else {
                simulador.intentarLeerOtraCuenta(prueba.uid)
            }
            _sincronizacion.update { it.copy(pruebaOtraCuenta = null, resultadoPrueba = resultado) }
        }
    }

    fun onResultadoPruebaCerrado() = _sincronizacion.update { it.copy(resultadoPrueba = null) }

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
