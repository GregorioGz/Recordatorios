package com.example.recordatorios.ui.detalle

import com.example.recordatorios.model.Recordatorio

/** Estados mutuamente excluyentes de la pantalla de detalle. */
sealed interface DetalleRecordatorioUiState {
    data object Cargando : DetalleRecordatorioUiState
    data class Encontrado(val recordatorio: Recordatorio) : DetalleRecordatorioUiState
    data class NoEncontrado(val id: Int) : DetalleRecordatorioUiState
    data class Error(val mensaje: String) : DetalleRecordatorioUiState
}
