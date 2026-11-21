package com.example.recordatorios.ui.crear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Validador
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del formulario: recibe eventos, aplica las validaciones y
 * actualiza el estado con nuevas instancias inmutables (update + copy).
 */
class CrearRecordatorioViewModel(
    private val repositorio: RecordatoriosRepository
) : ViewModel() {

    private val categoriasValidas = repositorio.categoriasDisponibles()

    private val _uiState = MutableStateFlow(
        CrearRecordatorioUiState(categoriasDisponibles = categoriasValidas.toList())
    )
    val uiState: StateFlow<CrearRecordatorioUiState> = _uiState.asStateFlow()

    // ---------- Eventos de la interfaz ----------
    fun onTituloCambiado(titulo: String) = actualizar(Campo.TITULO) { it.copy(titulo = titulo) }

    fun onDescripcionCambiada(descripcion: String) =
        actualizar(Campo.DESCRIPCION) { it.copy(descripcion = descripcion) }

    fun onCategoriaSeleccionada(categoria: Categoria) =
        actualizar(Campo.CATEGORIA) { it.copy(categoriaSeleccionada = categoria) }

    fun onPrioridadSeleccionada(prioridad: Prioridad) =
        actualizar(Campo.PRIORIDAD) { it.copy(prioridadSeleccionada = prioridad) }

    fun onGuardar() {
        // Al intentar guardar se muestran los errores de TODOS los campos
        _uiState.update { validar(it.copy(camposTocados = Campo.entries.toSet())) }
        val estado = _uiState.value
        if (!estado.formularioValido || estado.enviando) return

        val categoria = estado.categoriaSeleccionada ?: return
        val prioridad = estado.prioridadSeleccionada ?: return
        val borrador = BorradorRecordatorio(
            titulo = estado.titulo.trim(),
            descripcion = estado.descripcion.trim().ifBlank { null },
            categoria = categoria,
            prioridad = prioridad
        )

        viewModelScope.launch {
            _uiState.update { it.copy(envio = EstadoEnvio.Enviando) }
            val resultado = try {
                val creado = repositorio.guardar(borrador)
                EstadoEnvio.Exito(creado.id)
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
            initializer { CrearRecordatorioViewModel(AppContainer.repositorio) }
        }
    }
}
