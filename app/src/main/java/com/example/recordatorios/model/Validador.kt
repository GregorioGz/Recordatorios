package com.example.recordatorios.model

/**
 * Reglas de validación del producto. Se valida ANTES de construir el
 * Recordatorio, devolviendo un ResultadoValidacion en lugar de lanzar
 * una excepción, así la UI puede mostrar los errores.
 */
object Validador {

    const val MAX_TITULO = 60

    fun crearRecordatorio(
        id: Int,
        titulo: String,
        categoria: Categoria?,
        prioridad: Prioridad?,
        descripcion: String? = null,
        ubicacion: Ubicacion? = null,
        avisoMinutosAntes: Int? = null
    ): ResultadoValidacion {
        val errores = mutableListOf<String>()

        if (titulo.isBlank()) errores += "El título es obligatorio"
        if (titulo.length > MAX_TITULO) errores += "El título supera $MAX_TITULO caracteres"
        if (categoria == null) errores += "Debe elegir una categoría"
        if (prioridad == null) errores += "Debe elegir una prioridad"
        if (avisoMinutosAntes != null && avisoMinutosAntes <= 0) {
            errores += "El aviso debe ser mayor a 0 minutos"
        }
        if (ubicacion != null &&
            (ubicacion.latitud !in -90.0..90.0 || ubicacion.longitud !in -180.0..180.0)
        ) {
            errores += "Coordenadas de ubicación fuera de rango"
        }

        if (errores.isNotEmpty()) return ResultadoValidacion.Invalido(errores)

        return ResultadoValidacion.Valido(
            Recordatorio(
                id = id,
                titulo = titulo.trim(),
                categoria = categoria!!,
                prioridad = prioridad!!,
                descripcion = descripcion?.takeIf { it.isNotBlank() },
                ubicacion = ubicacion,
                avisoMinutosAntes = avisoMinutosAntes
            )
        )
    }
}
