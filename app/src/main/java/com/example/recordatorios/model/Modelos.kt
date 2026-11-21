package com.example.recordatorios.model

/**
 * Modelo de DOMINIO (evoluciona el de las actividades anteriores).
 * Es el único modelo que conocen los ViewModel y las pantallas: no depende
 * de Room (Entity) ni de Retrofit (DTO).
 * Todas las clases son inmutables (solo `val`): para "cambiar" un
 * recordatorio se crea una copia con copy().
 */

// ---------- Prioridad (sealed class) ----------
// `clave` es el valor estable que se guarda en Room y se envía al servidor.
sealed class Prioridad(val etiqueta: String, val nivel: Int, val clave: String) {
    data object Alta : Prioridad("Alta", 3, "ALTA")
    data object Media : Prioridad("Media", 2, "MEDIA")
    data object Baja : Prioridad("Baja", 1, "BAJA")

    companion object {
        // get(): la lista se crea al pedirla (corrección del error de la Actividad 1)
        val todas: List<Prioridad> get() = listOf(Alta, Media, Baja)

        /** null si la clave no corresponde a ninguna prioridad conocida. */
        fun desdeClave(clave: String?): Prioridad? = todas.firstOrNull { it.clave == clave }
    }
}

// ---------- Estado del recordatorio (sealed class) ----------
sealed class EstadoRecordatorio(val etiqueta: String, val clave: String) {
    data object Pendiente : EstadoRecordatorio("Pendiente", "PENDIENTE")
    data object EnCurso : EstadoRecordatorio("En curso", "EN_CURSO")
    data object Completado : EstadoRecordatorio("Completado", "COMPLETADO")

    companion object {
        val todos: List<EstadoRecordatorio> get() = listOf(Pendiente, EnCurso, Completado)

        fun desdeClave(clave: String?): EstadoRecordatorio? = todos.firstOrNull { it.clave == clave }
    }
}

// ---------- Estado de sincronización con el servidor ----------
enum class EstadoSync {
    /** Confirmado por el servidor. */
    SINCRONIZADO,

    /** Creado en el dispositivo; el servidor todavía no lo conoce. */
    PENDIENTE_CREAR,

    /** Modificado en el dispositivo después de la última confirmación. */
    PENDIENTE_ACTUALIZAR,

    /** Eliminado en el dispositivo; se oculta y se borra de Room cuando el servidor confirma. */
    PENDIENTE_ELIMINAR,

    /** El servidor rechazó o no pudo procesar el último envío: requiere reintento. */
    ERROR;

    val estaPendiente: Boolean get() = this != SINCRONIZADO
}

// ---------- Categoría (data class inmutable) ----------
// Al ser data class, dos categorías con el mismo id y nombre son iguales,
// por eso un Set<Categoria> descarta automáticamente las repetidas.
data class Categoria(
    val id: Int,
    val nombre: String
)

// ---------- Dato opcional de contexto ----------
data class Ubicacion(
    val nombre: String,
    val latitud: Double,
    val longitud: Double
)

// ---------- Recordatorio (data class inmutable) ----------
data class Recordatorio(
    val id: String,                       // UUID generado en el dispositivo (estable)
    val titulo: String,
    val categoria: Categoria,
    val prioridad: Prioridad,
    val estado: EstadoRecordatorio = EstadoRecordatorio.Pendiente,
    val descripcion: String? = null,      // opcional
    val ubicacion: Ubicacion? = null,     // opcional (se usará en módulos posteriores)
    val avisoMinutosAntes: Int? = null,   // opcional (se usará en módulos posteriores)
    val fechaCreacion: Long = 0L,         // epoch en milisegundos
    val fechaActualizacion: Long = 0L,
    val estadoSync: EstadoSync = EstadoSync.SINCRONIZADO,
    val errorSync: String? = null         // último mensaje de error de sincronización
) {
    // Regla de integridad: nunca puede existir un Recordatorio inválido.
    init {
        require(id.isNotBlank()) { "El identificador no puede estar vacío" }
        require(titulo.trim().length >= Validador.MIN_TITULO) {
            "El título debe tener al menos ${Validador.MIN_TITULO} caracteres"
        }
        require(titulo.length <= Validador.MAX_TITULO) {
            "El título no puede superar ${Validador.MAX_TITULO} caracteres"
        }
        require((descripcion?.length ?: 0) <= Validador.MAX_DESCRIPCION) {
            "La descripción no puede superar ${Validador.MAX_DESCRIPCION} caracteres"
        }
        require(avisoMinutosAntes == null || avisoMinutosAntes > 0) {
            "El aviso debe ser mayor a 0 minutos"
        }
    }
}

/** Datos que el formulario entrega al repositorio para crear o editar (sin id ni fechas). */
data class BorradorRecordatorio(
    val titulo: String,
    val descripcion: String?,
    val categoria: Categoria,
    val prioridad: Prioridad,
    val estado: EstadoRecordatorio = EstadoRecordatorio.Pendiente
)

/** Resumen global de la sincronización, para el indicador de la interfaz. */
data class EstadoSincronizacion(
    val hayConexion: Boolean = true,
    val sincronizando: Boolean = false,
    val pendientes: Int = 0,
    val ultimoError: String? = null
)
