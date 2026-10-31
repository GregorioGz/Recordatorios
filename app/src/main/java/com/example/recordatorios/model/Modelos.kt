package com.example.recordatorios.model

/**
 * Modelo de dominio de la app de recordatorios.
 * Todas las clases son inmutables (solo `val`) para evitar estados inválidos
 * modificados desde afuera. Para "cambiar" un recordatorio se usa copy().
 */

// ---------- Prioridad (sealed class) ----------
sealed class Prioridad(val etiqueta: String, val nivel: Int) {
    data object Alta : Prioridad("Alta", 3)
    data object Media : Prioridad("Media", 2)
    data object Baja : Prioridad("Baja", 1)

    companion object {
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
// Al ser data class, dos categorías con el mismo id y nombre son "iguales",
// por eso un Set<Categoria> descarta automáticamente las repetidas.
data class Categoria(
    val id: Int,
    val nombre: String
)

// ---------- Datos opcionales del contexto ----------
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
    val ubicacion: Ubicacion? = null,     // opcional (recordatorio por lugar)
    val avisoMinutosAntes: Int? = null    // opcional (recordatorio por aviso)
) {
    // Regla de integridad: nunca puede existir un Recordatorio inválido.
    init {
        require(titulo.isNotBlank()) { "El título no puede estar vacío" }
        require(titulo.length <= 60) { "El título no puede superar 60 caracteres" }
        require(avisoMinutosAntes == null || avisoMinutosAntes > 0) {
            "El aviso debe ser mayor a 0 minutos"
        }
    }
}

// ---------- Resultado de validación (sealed class) ----------
sealed class ResultadoValidacion {
    data class Valido(val recordatorio: Recordatorio) : ResultadoValidacion()
    data class Invalido(val errores: List<String>) : ResultadoValidacion()
}

// ---------- Estado de la pantalla (sealed interface) ----------
sealed interface UiState {
    data object Cargando : UiState
    data object Vacio : UiState
    data class Exito(val recordatorios: List<Recordatorio>) : UiState
    data class Error(val mensaje: String) : UiState
}
