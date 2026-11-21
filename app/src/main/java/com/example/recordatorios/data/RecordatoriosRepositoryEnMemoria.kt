package com.example.recordatorios.data

import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.Recordatorio
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementación en memoria del repositorio.
 * - delay() simula la demora de una base de datos o de la red (sin bloquear la UI).
 * - Los "errores simulados" fallan UNA sola vez, así el botón Reintentar funciona.
 */
class RecordatoriosRepositoryEnMemoria(
    private val demoraMs: Long = 1_200
) : RecordatoriosRepository, SimuladorDeEscenarios {

    private val mutex = Mutex()
    @Volatile private var recordatorios: List<Recordatorio> = DatosSimulados.recordatorios
    @Volatile private var fallarProximaCarga = false
    @Volatile private var fallarProximoGuardado = false

    override suspend fun obtenerTodos(): List<Recordatorio> {
        delay(demoraMs)
        return mutex.withLock {
            if (fallarProximaCarga) {
                fallarProximaCarga = false
                throw IllegalStateException("No se pudieron cargar los recordatorios. Revisá la conexión e intentá de nuevo.")
            }
            recordatorios
        }
    }

    override suspend fun obtenerPorId(id: Int): Recordatorio? {
        delay(demoraMs / 2)
        return mutex.withLock { recordatorios.firstOrNull { it.id == id } }
    }

    override suspend fun guardar(borrador: BorradorRecordatorio): Recordatorio {
        delay(demoraMs)
        return mutex.withLock {
            if (fallarProximoGuardado) {
                fallarProximoGuardado = false
                throw IllegalStateException("No se pudo guardar el recordatorio. Intentá nuevamente.")
            }
            val nuevo = Recordatorio(
                id = (recordatorios.maxOfOrNull { it.id } ?: 0) + 1,
                titulo = borrador.titulo,
                categoria = borrador.categoria,
                prioridad = borrador.prioridad,
                estado = EstadoRecordatorio.Pendiente,
                descripcion = borrador.descripcion
            )
            // Nueva lista inmutable en lugar de modificar la anterior
            recordatorios = recordatorios + nuevo
            nuevo
        }
    }

    override fun categoriasDisponibles(): Set<Categoria> = DatosSimulados.categorias

    // ---------- Simulación ----------
    override fun simularErrorEnProximaCarga() { fallarProximaCarga = true }
    override fun simularErrorEnProximoGuardado() { fallarProximoGuardado = true }
    override fun vaciarDatos() { recordatorios = emptyList() }
    override fun restaurarDatosDeEjemplo() { recordatorios = DatosSimulados.recordatorios }
}
