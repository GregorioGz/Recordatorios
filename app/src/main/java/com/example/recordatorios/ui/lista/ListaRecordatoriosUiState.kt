package com.example.recordatorios.ui.lista

import com.example.recordatorios.model.Recordatorio

/**
 * Estados MUTUAMENTE EXCLUYENTES del listado (sealed interface).
 * Es imposible estar "cargando" y "con error" al mismo tiempo.
 */
sealed interface ListaRecordatoriosUiState {
    data object Cargando : ListaRecordatoriosUiState
    data object Vacio : ListaRecordatoriosUiState
    data class ConDatos(val recordatorios: List<Recordatorio>) : ListaRecordatoriosUiState
    data class Error(val mensaje: String) : ListaRecordatoriosUiState
}

/** Escenarios que se pueden simular desde el menú ⋮ del listado. */
enum class Simulacion(val etiqueta: String) {
    LISTA_VACIA("Simular lista vacía"),
    ERROR_DE_CARGA("Simular error de carga"),
    ERROR_AL_GUARDAR("Simular error en el próximo guardado"),
    RESTAURAR("Restaurar datos de ejemplo")
}
