package com.example.recordatorios.ui.lista

import com.example.recordatorios.model.EstadoSincronizacion
import com.example.recordatorios.model.Recordatorio

/**
 * Estados MUTUAMENTE EXCLUYENTES del contenido del listado (sealed interface).
 * Es imposible estar "cargando" y "con error" al mismo tiempo.
 */
sealed interface ListaRecordatoriosUiState {
    data object Cargando : ListaRecordatoriosUiState

    /** ocultosPorAjustes: los ajustes (filtro/completados) pueden estar escondiendo recordatorios. */
    data class Vacio(val ocultosPorAjustes: Boolean = false) : ListaRecordatoriosUiState
    data class ConDatos(val recordatorios: List<Recordatorio>) : ListaRecordatoriosUiState
    data class Error(val mensaje: String) : ListaRecordatoriosUiState
}

/**
 * Estado de la barra de sincronización y del menú de pruebas (independiente del contenido).
 * - estado: conexión, sincronización en curso, pendientes y último error.
 * - escenarioServidorActivo: opción del servidor simulado marcada en el menú (null = servidor real).
 * - mensaje: aviso puntual para mostrar en un Snackbar.
 */
data class SincronizacionUiState(
    val estado: EstadoSincronizacion = EstadoSincronizacion(),
    val escenarioServidorActivo: Simulacion? = null,
    val mensaje: String? = null
)

enum class GrupoSimulacion { DATOS_LOCALES, PREFERENCIAS, SERVIDOR }

/** Escenarios que se pueden simular desde el menú ⋮ del listado (solo para pruebas). */
enum class Simulacion(val etiqueta: String, val grupo: GrupoSimulacion) {
    LISTA_VACIA("Simular lista vacía", GrupoSimulacion.DATOS_LOCALES),
    ERROR_DE_CARGA("Simular error de lectura local", GrupoSimulacion.DATOS_LOCALES),
    ERROR_AL_GUARDAR("Simular error en el próximo guardado", GrupoSimulacion.DATOS_LOCALES),
    RESTAURAR("Restaurar datos de ejemplo", GrupoSimulacion.DATOS_LOCALES),
    TEMA_INVALIDO("Guardar tema inválido (DataStore)", GrupoSimulacion.PREFERENCIAS),
    SERVIDOR_NORMAL("Servidor: normal (200/201/204)", GrupoSimulacion.SERVIDOR),
    SERVIDOR_ERROR_500("Servidor: error 500", GrupoSimulacion.SERVIDOR),
    SERVIDOR_ERROR_400("Servidor: solicitud inválida 400", GrupoSimulacion.SERVIDOR),
    SERVIDOR_TIMEOUT("Servidor: timeout", GrupoSimulacion.SERVIDOR),
    SERVIDOR_SIN_CONEXION("Servidor: sin conexión", GrupoSimulacion.SERVIDOR),
    SINCRONIZACION_CONCURRENTE("Probar 3 sincronizaciones simultáneas", GrupoSimulacion.SERVIDOR)
}
