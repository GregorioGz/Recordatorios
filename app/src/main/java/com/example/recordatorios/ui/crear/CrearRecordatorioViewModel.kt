package com.example.recordatorios.ui.crear

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Validador
import com.example.recordatorios.navigation.Rutas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del formulario (crear y editar): recibe eventos, aplica las
 * validaciones y actualiza el estado con nuevas instancias inmutables (update + copy).
 * Si la ruta trae un recordatorioId (ruta "editar/{id}"), trabaja en modo edición.
 */
class CrearRecordatorioViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: RecordatoriosRepository
) : ViewModel() {

    /** null = crear; con valor = editar ese recordatorio. */
    private val idEnEdicion: String? = savedStateHandle.get<String>(Rutas.ARG_RECORDATORIO_ID)

    private val categoriasValidas = repositorio.categoriasDisponibles()

    private val _uiState = MutableStateFlow(
        CrearRecordatorioUiState(
            modoEdicion = idEnEdicion != null,
            cargandoDatos = idEnEdicion != null,
            categoriasDisponibles = categoriasValidas.toList()
        )
    )
    val uiState: StateFlow<CrearRecordatorioUiState> = _uiState.asStateFlow()

    init {
        idEnEdicion?.let { cargarParaEditar(it) }
    }

    /** Lee UNA vez el registro desde Room y completa el formulario. */
    private fun cargarParaEditar(id: String) {
        viewModelScope.launch {
            val recordatorio = try {
                repositorio.observarRecordatorio(id).first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _uiState.update { actual ->
                if (recordatorio == null) {
                    actual.copy(cargandoDatos = false, noEncontrado = true)
                } else {
                    validar(
                        actual.copy(
                            cargandoDatos = false,
                            titulo = recordatorio.titulo,
                            descripcion = recordatorio.descripcion.orEmpty(),
                            categoriaSeleccionada = recordatorio.categoria,
                            prioridadSeleccionada = recordatorio.prioridad,
                            estadoSeleccionado = recordatorio.estado,
                            // Los datos guardados ya se muestran validados
                            camposTocados = Campo.entries.toSet()
                        )
                    )
                }
            }
        }
    }

    // ---------- Eventos de la interfaz ----------
    fun onTituloCambiado(titulo: String) = actualizar(Campo.TITULO) { it.copy(titulo = titulo) }

    fun onDescripcionCambiada(descripcion: String) =
        actualizar(Campo.DESCRIPCION) { it.copy(descripcion = descripcion) }

    fun onCategoriaSeleccionada(categoria: Categoria) =
        actualizar(Campo.CATEGORIA) { it.copy(categoriaSeleccionada = categoria) }

    fun onPrioridadSeleccionada(prioridad: Prioridad) =
        actualizar(Campo.PRIORIDAD) { it.copy(prioridadSeleccionada = prioridad) }

    fun onEstadoSeleccionado(estado: EstadoRecordatorio) =
        _uiState.update { if (it.enviando) it else it.copy(estadoSeleccionado = estado) }

    fun onGuardar() {
        // Al intentar guardar se muestran los errores de TODOS los campos
        _uiState.update { validar(it.copy(camposTocados = Campo.entries.toSet())) }
        val estado = _uiState.value
        if (!estado.puedeGuardar) return

        val categoria = estado.categoriaSeleccionada ?: return
        val prioridad = estado.prioridadSeleccionada ?: return
        val borrador = BorradorRecordatorio(
            titulo = estado.titulo.trim(),
            descripcion = estado.descripcion.trim().ifBlank { null },
            categoria = categoria,
            prioridad = prioridad,
            estado = estado.estadoSeleccionado
        )

        viewModelScope.launch {
            _uiState.update { it.copy(envio = EstadoEnvio.Enviando) }
            val resultado = try {
                // Se guarda en Room (local-first); la sincronización ocurre después, en segundo plano
                val guardado = if (idEnEdicion != null) {
                    repositorio.actualizar(idEnEdicion, borrador)
                } else {
                    repositorio.crear(borrador)
                }
                EstadoEnvio.Exito(guardado.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoEnvio.Error(e.message ?: "No se pudo guardar el recordatorio.")
            }
            _uiState.update { it.copy(envio = resultado) }
        }
    }

    fun onReintentarGuardado() = onGuardar()

    fun onErrorDescartado() {
        _uiState.update { if (it.envio is EstadoEnvio.Error) it.copy(envio = EstadoEnvio.Inactivo) else it }
    }

    // ---------- Lógica interna ----------
    private fun actualizar(campo: Campo, cambio: (CrearRecordatorioUiState) -> CrearRecordatorioUiState) {
        _uiState.update { actual ->
            if (actual.enviando) return@update actual  // no se edita durante el envío
            val modificado = cambio(actual).copy(camposTocados = actual.camposTocados + campo)
            validar(modificado)
        }
    }

    /** Aplica TODAS las reglas y muestra solo los errores de los campos ya tocados. */
    private fun validar(estado: CrearRecordatorioUiState): CrearRecordatorioUiState {
        val errorTitulo = Validador.validarTitulo(estado.titulo)
        val errorDescripcion = Validador.validarDescripcion(estado.descripcion)
        val errorCategoria = Validador.validarCategoria(estado.categoriaSeleccionada, categoriasValidas)
        val errorPrioridad = Validador.validarPrioridad(estado.prioridadSeleccionada)
        val tocados = estado.camposTocados

        return estado.copy(
            errorTitulo = errorTitulo.takeIf { Campo.TITULO in tocados },
            errorDescripcion = errorDescripcion.takeIf { Campo.DESCRIPCION in tocados },
            errorCategoria = errorCategoria.takeIf { Campo.CATEGORIA in tocados },
            errorPrioridad = errorPrioridad.takeIf { Campo.PRIORIDAD in tocados },
            formularioValido = listOf(errorTitulo, errorDescripcion, errorCategoria, errorPrioridad)
                .all { it == null }
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { CrearRecordatorioViewModel(createSavedStateHandle(), AppContainer.repositorio) }
        }
    }
}
