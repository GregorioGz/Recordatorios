package com.example.recordatorios.ui.detalle

import com.example.recordatorios.model.Recordatorio

/** Estados mutuamente excluyentes de la pantalla de detalle. */
sealed interface DetalleRecordatorioUiState {
    data object Cargando : DetalleRecordatorioUiState

    /**
     * confirmandoEliminacion: el diálogo de confirmación está visible.
     * mensaje: aviso puntual (por ejemplo, un error al cambiar el estado).
     */
    data class Encontrado(
        val recordatorio: Recordatorio,
        val confirmandoEliminacion: Boolean = false,
        val mensaje: String? = null
    ) : DetalleRecordatorioUiState

    data class NoEncontrado(val id: String) : DetalleRecordatorioUiState

    /** El recordatorio se eliminó desde esta pantalla: hay que volver al listado. */
    data object Eliminado : DetalleRecordatorioUiState

    data class Error(val mensaje: String) : DetalleRecordatorioUiState
}
