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
 * - mensaje: aviso puntual para mostrar en un Snackbar.
 */
data class SincronizacionUiState(
    val estado: EstadoSincronizacion = EstadoSincronizacion(),
    val mensaje: String? = null,
    /** true solo en el flavor dev: muestra el menú ⋮ de pruebas. */
    val herramientasDePrueba: Boolean = false,
    /** Diálogo abierto para la prueba de acceso a otra cuenta (null = cerrado). */
    val pruebaOtraCuenta: PruebaOtraCuentaUiState? = null,
    /** Resultado de la última prueba (se muestra en un diálogo para poder capturarlo). */
    val resultadoPrueba: String? = null
)

/** Prueba de autorización: se ingresa el uid de otra cuenta y se intenta leer o escribir. */
data class PruebaOtraCuentaUiState(
    val operacion: Simulacion,
    val uid: String = "",
    val ejecutando: Boolean = false
)

enum class GrupoSimulacion { DATOS_LOCALES, PREFERENCIAS, NUBE, AUTORIZACION }

/** Herramientas del menú ⋮ del listado (solo en el flavor dev). */
enum class Simulacion(val etiqueta: String, val grupo: GrupoSimulacion) {
    LISTA_VACIA("Simular lista vacía", GrupoSimulacion.DATOS_LOCALES),
    ERROR_DE_CARGA("Simular error de lectura local", GrupoSimulacion.DATOS_LOCALES),
    ERROR_AL_GUARDAR("Simular error en el próximo guardado", GrupoSimulacion.DATOS_LOCALES),
    CARGAR_EJEMPLOS("Cargar datos de ejemplo", GrupoSimulacion.DATOS_LOCALES),
    TEMA_INVALIDO("Guardar tema inválido (DataStore)", GrupoSimulacion.PREFERENCIAS),
    SINCRONIZACION_CONCURRENTE("Probar 3 sincronizaciones simultáneas", GrupoSimulacion.NUBE),
    LEER_OTRA_CUENTA("Intentar leer datos de otra cuenta", GrupoSimulacion.AUTORIZACION),
    ESCRIBIR_OTRA_CUENTA("Intentar escribir en otra cuenta", GrupoSimulacion.AUTORIZACION)
}
