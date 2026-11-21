package com.example.recordatorios.ui.crear

import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.Prioridad

/** Estado del envío: mutuamente excluyente (sealed interface). */
sealed interface EstadoEnvio {
    data object Inactivo : EstadoEnvio
    data object Enviando : EstadoEnvio
    data class Exito(val idCreado: Int) : EstadoEnvio
    data class Error(val mensaje: String) : EstadoEnvio
}

/** Campos del formulario (para saber cuáles ya tocó la persona usuaria). */
enum class Campo { TITULO, DESCRIPCION, CATEGORIA, PRIORIDAD }

/**
 * Estado INMUTABLE de la pantalla de creación: valores actuales, mensajes de
 * validación, estado de envío, resultado (éxito) y error general de guardado.
 * Cada cambio produce una nueva instancia con copy().
 */
data class CrearRecordatorioUiState(
    // Valores actuales de los campos
    val titulo: String = "",
    val descripcion: String = "",
    val categoriaSeleccionada: Categoria? = null,
    val prioridadSeleccionada: Prioridad? = null,
    // Opciones definidas
    val categoriasDisponibles: List<Categoria> = emptyList(),
    val prioridadesDisponibles: List<Prioridad> = Prioridad.todas,
    // Mensajes de validación (null = sin error visible)
    val errorTitulo: String? = null,
    val errorDescripcion: String? = null,
    val errorCategoria: String? = null,
    val errorPrioridad: String? = null,
    val formularioValido: Boolean = false,
    val camposTocados: Set<Campo> = emptySet(),
    // Envío: Inactivo / Enviando / Exito / Error (error general de guardado)
    val envio: EstadoEnvio = EstadoEnvio.Inactivo
) {
    val enviando: Boolean get() = envio is EstadoEnvio.Enviando

    /** El botón Guardar se habilita solo si el formulario es válido y no hay un envío en curso. */
    val puedeGuardar: Boolean get() = formularioValido && !enviando
}
