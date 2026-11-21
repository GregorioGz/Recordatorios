package com.example.recordatorios.fakes

import com.example.recordatorios.data.local.RecordatorioDao
import com.example.recordatorios.data.local.RecordatorioEntity
import com.example.recordatorios.data.remote.RecordatorioDto
import com.example.recordatorios.data.remote.RecordatoriosApi
import com.example.recordatorios.model.EstadoSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** DAO en memoria con el mismo comportamiento que las consultas de Room. */
class FakeRecordatorioDao : RecordatorioDao {
    val filas = MutableStateFlow<Map<String, RecordatorioEntity>>(emptyMap())

    private fun visibles(): Flow<List<RecordatorioEntity>> = filas.map { mapa ->
        mapa.values.filter { it.estadoSync != EstadoSync.PENDIENTE_ELIMINAR }.sortedByDescending { it.fechaCreacion }
    }

    override fun observarTodos() = visibles()
    override fun observarPorPrioridad(prioridad: String) = visibles().map { l -> l.filter { it.prioridad == prioridad } }
    override fun observarPorEstado(estado: String) = visibles().map { l -> l.filter { it.estado == estado } }
    override fun observarPorId(id: String) =
        filas.map { it[id]?.takeIf { e -> e.estadoSync != EstadoSync.PENDIENTE_ELIMINAR } }

    override fun observarCantidadPendientes() =
        filas.map { mapa -> mapa.values.count { it.estadoSync != EstadoSync.SINCRONIZADO } }

    override suspend fun obtenerPorId(id: String) = filas.value[id]

    override suspend fun obtenerPendientes() =
        filas.value.values.filter { it.estadoSync != EstadoSync.SINCRONIZADO }.sortedBy { it.fechaActualizacion }

    override suspend fun insertar(recordatorio: RecordatorioEntity) {
        check(recordatorio.id !in filas.value) { "UNIQUE constraint failed: recordatorios.id" }
        filas.update { it + (recordatorio.id to recordatorio) }
    }

    override suspend fun insertarSiNoExiste(recordatorio: RecordatorioEntity): Long {
        if (recordatorio.id in filas.value) return -1
        filas.update { it + (recordatorio.id to recordatorio) }
        return 1
    }

    override suspend fun upsert(recordatorio: RecordatorioEntity) {
        filas.update { it + (recordatorio.id to recordatorio) }
    }

    override suspend fun actualizar(recordatorio: RecordatorioEntity): Int {
        if (recordatorio.id !in filas.value) return 0
        filas.update { it + (recordatorio.id to recordatorio) }
        return 1
    }

    override suspend fun eliminarPorId(id: String): Int {
        if (id !in filas.value) return 0
        filas.update { it - id }
        return 1
    }

    override suspend fun marcarEstadoSync(
        id: String,
        fechaActualizacion: Long,
        estadoSync: EstadoSync,
        errorSync: String?
    ): Int {
        val actual = filas.value[id] ?: return 0
        if (actual.fechaActualizacion != fechaActualizacion) return 0
        filas.update { it + (id to actual.copy(estadoSync = estadoSync, errorSync = errorSync)) }
        return 1
    }

    override suspend fun marcarSincronizadosComoPendientes(): Int {
        var cambiados = 0
        filas.update { mapa ->
            mapa.mapValues { (_, e) ->
                if (e.estadoSync == EstadoSync.SINCRONIZADO) {
                    cambiados++; e.copy(estadoSync = EstadoSync.PENDIENTE_ACTUALIZAR)
                } else e
            }
        }
        return cambiados
    }
}

/**
 * API falsa: servidor en memoria con upsert por id y contadores de llamadas.
 * Cada llamada demora [demoraMs] (tiempo virtual de runTest) para que las
 * sincronizaciones concurrentes realmente se superpongan.
 */
class FakeRecordatoriosApi(private val demoraMs: Long = 100) : RecordatoriosApi {
    val servidor = LinkedHashMap<String, RecordatorioDto>()
    val putPorId = mutableMapOf<String, Int>()
    val deletePorId = mutableMapOf<String, Int>()

    /** Si tiene valor, los PUT responden con ese código de error. */
    var codigoErrorEnPut: Int? = null

    /** Si tiene valor, TODAS las llamadas lanzan esta excepción. */
    var excepcion: Throwable? = null

    override suspend fun obtenerTodos(): Response<List<RecordatorioDto>> {
        delay(demoraMs)
        excepcion?.let { throw it }
        return Response.success(servidor.values.toList())
    }

    override suspend fun guardar(id: String, recordatorio: RecordatorioDto): Response<RecordatorioDto> {
        delay(demoraMs)
        excepcion?.let { throw it }
        putPorId[id] = (putPorId[id] ?: 0) + 1
        codigoErrorEnPut?.let { return Response.error(it, cuerpoError()) }
        val existia = servidor.put(id, recordatorio) != null
        return Response.success(if (existia) 200 else 201, recordatorio)
    }

    override suspend fun eliminar(id: String): Response<Unit> {
        delay(demoraMs)
        excepcion?.let { throw it }
        deletePorId[id] = (deletePorId[id] ?: 0) + 1
        return if (servidor.remove(id) != null) Response.success<Unit>(204, null)
        else Response.error(404, cuerpoError())
    }

    private fun cuerpoError() = """{"error":"x"}""".toResponseBody("application/json".toMediaType())
}

/** Entidad de prueba válida. */
fun entidad(
    id: String,
    estadoSync: EstadoSync = EstadoSync.SINCRONIZADO,
    titulo: String = "Recordatorio $id",
    fecha: Long = 1_000L
) = RecordatorioEntity(
    id = id,
    titulo = titulo,
    descripcion = null,
    categoriaId = 1,
    categoriaNombre = "Trabajo",
    prioridad = "ALTA",
    estado = "PENDIENTE",
    ubicacionNombre = null,
    ubicacionLatitud = null,
    ubicacionLongitud = null,
    avisoMinutosAntes = null,
    fechaCreacion = fecha,
    fechaActualizacion = fecha,
    estadoSync = estadoSync,
    errorSync = null
)
