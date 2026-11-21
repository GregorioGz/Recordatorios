package com.example.recordatorios.model

/**
 * Modelo de dominio (se conserva de la Actividad 1).
 * Todas las clases son inmutables (solo `val`): para "cambiar" un
 * recordatorio se crea una copia con copy().
 */

// ---------- Prioridad (sealed class) ----------
sealed class Prioridad(val etiqueta: String, val nivel: Int) {
    data object Alta : Prioridad("Alta", 3)
    data object Media : Prioridad("Media", 2)
    data object Baja : Prioridad("Baja", 1)

    companion object {
        // get(): la lista se crea al pedirla (corrección del error de la Actividad 1)
        val todas: List<Prioridad> get() = listOf(Alta, Media, Baja)
    }
}

// ---------- Estado del recordatorio (sealed class) ----------
sealed class EstadoRecordatorio(val etiqueta: String) {
    data object Pendiente : EstadoRecordatorio("Pendiente")
    data object EnCurso : EstadoRecordatorio("En curso")
    data object Completado : EstadoRecordatorio("Completado")

    companion object {
        val todos: List<EstadoRecordatorio> get() = listOf(Pendiente, EnCurso, Completado)
    }
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
    val id: Int,
    val titulo: String,
    val categoria: Categoria,
    val prioridad: Prioridad,
    val estado: EstadoRecordatorio = EstadoRecordatorio.Pendiente,
    val descripcion: String? = null,      // opcional
    val ubicacion: Ubicacion? = null,     // opcional (se usará en módulos posteriores)
    val avisoMinutosAntes: Int? = null    // opcional (se usará en módulos posteriores)
) {
    // Regla de integridad: nunca puede existir un Recordatorio inválido.
    init {
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

/** Datos que la pantalla de creación entrega al repositorio (todavía sin id). */
data class BorradorRecordatorio(
    val titulo: String,
    val descripcion: String?,
    val categoria: Categoria,
    val prioridad: Prioridad
)
