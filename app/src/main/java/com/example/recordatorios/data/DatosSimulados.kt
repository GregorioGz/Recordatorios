package com.example.recordatorios.data

import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.model.Ubicacion

/**
 * Datos de ejemplo con los que arranca el repositorio en memoria.
 *  - List -> recordatorios iniciales
 *  - Set  -> categorías definidas (sin repetidas)
 */
object DatosSimulados {

    val trabajo = Categoria(1, "Trabajo")
    val estudio = Categoria(2, "Estudio")
    val salud = Categoria(3, "Salud")
    val hogar = Categoria(4, "Hogar")

    // Set: las únicas categorías válidas del formulario
    val categorias: Set<Categoria> = setOf(trabajo, estudio, salud, hogar)

    val recordatorios: List<Recordatorio> = listOf(
        Recordatorio(
            id = 1,
            titulo = "Entregar Actividad 2",
            categoria = estudio,
            prioridad = Prioridad.Alta,
            estado = EstadoRecordatorio.EnCurso,
            descripcion = "Subir el PDF con capturas, tabla de rutas y descripción de la arquitectura",
            avisoMinutosAntes = 60
        ),
        Recordatorio(
            id = 2,
            titulo = "Reunión de equipo",
            categoria = trabajo,
            prioridad = Prioridad.Media,
            descripcion = "Revisar avances del sprint",
            ubicacion = Ubicacion("Oficina central", -34.6037, -58.3816)
        ),
        Recordatorio(
            id = 3,
            titulo = "Turno con el médico",
            categoria = salud,
            prioridad = Prioridad.Alta,
            avisoMinutosAntes = 120
        ),
        Recordatorio(
            id = 4,
            titulo = "Comprar verduras",
            categoria = hogar,
            prioridad = Prioridad.Baja,
            estado = EstadoRecordatorio.Completado,
            ubicacion = Ubicacion("Verdulería del barrio", -34.6100, -58.3900)
        ),
        Recordatorio(
            id = 5,
            titulo = "Repasar Navigation Compose",
            categoria = estudio,
            prioridad = Prioridad.Media,
            descripcion = "NavHost, rutas con argumentos y popBackStack()"
        ),
        Recordatorio(
            id = 6,
            titulo = "Enviar informe mensual",
            categoria = trabajo,
            prioridad = Prioridad.Alta,
            estado = EstadoRecordatorio.Completado
        )
    )
}
