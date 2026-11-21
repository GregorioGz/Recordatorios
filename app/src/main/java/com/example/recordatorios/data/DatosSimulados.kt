package com.example.recordatorios.data

import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.model.Ubicacion

/**
 * Datos de ejemplo.
 *  - List -> recordatorios que se insertan en Room SOLO al crear la base (primera ejecución)
 *  - Set  -> categorías definidas (sin repetidas)
 * Los ids son UUID fijos: si se vuelven a insertar, no se duplican.
 */
object DatosSimulados {

    val trabajo = Categoria(1, "Trabajo")
    val estudio = Categoria(2, "Estudio")
    val salud = Categoria(3, "Salud")
    val hogar = Categoria(4, "Hogar")

    // Set: las únicas categorías válidas del formulario
    val categorias: Set<Categoria> = setOf(trabajo, estudio, salud, hogar)

    // Fecha de referencia fija para que el orden de los ejemplos sea estable
    private const val BASE = 1_761_000_000_000L
    private const val HORA = 3_600_000L

    val recordatorios: List<Recordatorio> = listOf(
        Recordatorio(
            id = "5f1c2a90-0b6e-4d3a-9a41-000000000001",
            titulo = "Entregar Actividad 3",
            categoria = estudio,
            prioridad = Prioridad.Alta,
            estado = EstadoRecordatorio.EnCurso,
            descripcion = "Subir el proyecto con persistencia local, sincronización y el informe",
            avisoMinutosAntes = 60,
            fechaCreacion = BASE, fechaActualizacion = BASE
        ),
        Recordatorio(
            id = "5f1c2a90-0b6e-4d3a-9a41-000000000002",
            titulo = "Reunión de equipo",
            categoria = trabajo,
            prioridad = Prioridad.Media,
            descripcion = "Revisar avances del sprint",
            ubicacion = Ubicacion("Oficina central", -34.6037, -58.3816),
            fechaCreacion = BASE - HORA, fechaActualizacion = BASE - HORA
        ),
        Recordatorio(
            id = "5f1c2a90-0b6e-4d3a-9a41-000000000003",
            titulo = "Turno con el médico",
            categoria = salud,
            prioridad = Prioridad.Alta,
            avisoMinutosAntes = 120,
            fechaCreacion = BASE - 2 * HORA, fechaActualizacion = BASE - 2 * HORA
        ),
        Recordatorio(
            id = "5f1c2a90-0b6e-4d3a-9a41-000000000004",
            titulo = "Comprar verduras",
            categoria = hogar,
            prioridad = Prioridad.Baja,
            estado = EstadoRecordatorio.Completado,
            ubicacion = Ubicacion("Verdulería del barrio", -34.6100, -58.3900),
            fechaCreacion = BASE - 3 * HORA, fechaActualizacion = BASE - 3 * HORA
        ),
        Recordatorio(
            id = "5f1c2a90-0b6e-4d3a-9a41-000000000005",
            titulo = "Repasar Room y DataStore",
            categoria = estudio,
            prioridad = Prioridad.Media,
            descripcion = "Entity, DAO, Flow y preferencias con claves tipadas",
            fechaCreacion = BASE - 4 * HORA, fechaActualizacion = BASE - 4 * HORA
        ),
        Recordatorio(
            id = "5f1c2a90-0b6e-4d3a-9a41-000000000006",
            titulo = "Enviar informe mensual",
            categoria = trabajo,
            prioridad = Prioridad.Alta,
            estado = EstadoRecordatorio.Completado,
            fechaCreacion = BASE - 5 * HORA, fechaActualizacion = BASE - 5 * HORA
        )
    )

    /** Busca la categoría definida por id; si no existe (dato remoto nuevo) se crea con el nombre recibido. */
    fun categoriaPara(id: Int, nombre: String): Categoria =
        categorias.firstOrNull { it.id == id } ?: Categoria(id, nombre)
}
