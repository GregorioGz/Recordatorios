package com.example.recordatorios.model

/**
 * Reglas de validación del formulario. Son funciones puras: reciben un valor
 * y devuelven el mensaje de error, o null si el valor es válido.
 * Las usa el CrearRecordatorioViewModel; las pantallas solo muestran el resultado.
 */
object Validador {

    const val MIN_TITULO = 4
    const val MAX_TITULO = 60
    const val MAX_DESCRIPCION = 200

    fun validarTitulo(titulo: String): String? {
        val limpio = titulo.trim()
        return when {
            limpio.length < MIN_TITULO ->
                "El título debe tener al menos $MIN_TITULO caracteres (sin contar espacios al inicio o al final)"
            limpio.length > MAX_TITULO ->
                "El título no puede superar $MAX_TITULO caracteres"
            else -> null
        }
    }

    fun validarDescripcion(descripcion: String): String? =
        if (descripcion.length > MAX_DESCRIPCION) {
            "La descripción no puede superar $MAX_DESCRIPCION caracteres (${descripcion.length}/$MAX_DESCRIPCION)"
        } else {
            null
        }

    fun validarCategoria(categoria: Categoria?, opciones: Set<Categoria>): String? = when {
        categoria == null -> "Seleccioná una categoría"
        categoria !in opciones -> "La categoría elegida no pertenece a las opciones definidas"
        else -> null
    }

    fun validarPrioridad(prioridad: Prioridad?): String? =
        if (prioridad == null) "Seleccioná una prioridad" else null
}
