package com.example.recordatorios.data.preferencias

/**
 * Modelo PROPIO de preferencias: el resto de la app nunca ve las claves ni
 * el objeto Preferences de DataStore, solo esta clase inmutable.
 * Los valores por defecto de cada campo son los PREDETERMINADOS que se usan
 * cuando no hay datos guardados, el valor es desconocido o falla la lectura.
 */
data class PreferenciasUsuario(
    val tema: Tema = Tema.SISTEMA,
    val mostrarCompletados: Boolean = true,
    val orden: Orden = Orden.FECHA,
    val filtroPrioridad: FiltroPrioridad = FiltroPrioridad.TODAS
)

/**
 * Cada opción tiene una `clave` estable que es lo que se guarda en disco.
 * Así, renombrar una constante de Kotlin no rompe los datos ya guardados.
 */
enum class Tema(val clave: String, val etiqueta: String) {
    SISTEMA("SISTEMA", "Según el sistema"),
    CLARO("CLARO", "Claro"),
    OSCURO("OSCURO", "Oscuro");

    companion object {
        /** Valor desconocido o ausente -> predeterminado (nunca lanza excepción). */
        fun desdeClave(clave: String?): Tema = entries.firstOrNull { it.clave == clave } ?: SISTEMA
    }
}

enum class Orden(val clave: String, val etiqueta: String) {
    FECHA("FECHA", "Más recientes"),
    PRIORIDAD("PRIORIDAD", "Prioridad"),
    TITULO("TITULO", "Título (A-Z)");

    companion object {
        fun desdeClave(clave: String?): Orden = entries.firstOrNull { it.clave == clave } ?: FECHA
    }
}

enum class FiltroPrioridad(val clave: String, val etiqueta: String) {
    TODAS("TODAS", "Todas"),
    ALTA("ALTA", "Alta"),
    MEDIA("MEDIA", "Media"),
    BAJA("BAJA", "Baja");

    companion object {
        fun desdeClave(clave: String?): FiltroPrioridad =
            entries.firstOrNull { it.clave == clave } ?: TODAS
    }
}
