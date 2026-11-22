package com.example.recordatorios.fakes

import com.example.recordatorios.data.auth.AuthRepository
import com.example.recordatorios.data.auth.ResultadoAuth
import com.example.recordatorios.data.aMapaFirestore
import com.example.recordatorios.data.local.RecordatorioDao
import com.example.recordatorios.data.local.RecordatorioEntity
import com.example.recordatorios.data.remote.CambioRemoto
import com.example.recordatorios.data.remote.DocumentoRemoto
import com.example.recordatorios.data.remote.FuenteRemotaRecordatorios
import com.example.recordatorios.data.remote.ResultadoRemoto
import com.example.recordatorios.model.EstadoSesion
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Usuario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/** DAO en memoria con el mismo comportamiento que las consultas de Room (incluido el filtro por uid). */
class FakeRecordatorioDao : RecordatorioDao {
    val filas = MutableStateFlow<Map<String, RecordatorioEntity>>(emptyMap())

    private fun de(uid: String) = filas.value.values.filter { it.uidPropietario == uid }

    private fun visibles(uid: String): Flow<List<RecordatorioEntity>> = filas.map { mapa ->
        mapa.values
            .filter { it.uidPropietario == uid && it.estadoSync != EstadoSync.PENDIENTE_ELIMINAR }
            .sortedByDescending { it.fechaCreacion }
    }

    override fun observarTodos(uid: String) = visibles(uid)
    override fun observarPorPrioridad(uid: String, prioridad: String) =
        visibles(uid).map { l -> l.filter { it.prioridad == prioridad } }

    override fun observarPorId(uid: String, id: String) = filas.map { mapa ->
        mapa[id]?.takeIf { it.uidPropietario == uid && it.estadoSync != EstadoSync.PENDIENTE_ELIMINAR }
    }

    override fun observarCantidadPendientes(uid: String) = filas.map { mapa ->
        mapa.values.count { it.uidPropietario == uid && it.estadoSync != EstadoSync.SINCRONIZADO }
    }

    override suspend fun obtenerPorId(uid: String, id: String) = filas.value[id]?.takeIf { it.uidPropietario == uid }
    override suspend fun contarPendientes(uid: String) = de(uid).count { it.estadoSync != EstadoSync.SINCRONIZADO }
    override suspend fun contarVisibles(uid: String) = de(uid).count { it.estadoSync != EstadoSync.PENDIENTE_ELIMINAR }
    override suspend fun obtenerPendientes(uid: String) =
        de(uid).filter { it.estadoSync != EstadoSync.SINCRONIZADO }.sortedBy { it.fechaActualizacion }

    override suspend fun idsSincronizados(uid: String) =
        de(uid).filter { it.estadoSync == EstadoSync.SINCRONIZADO }.map { it.id }

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

    override suspend fun eliminarPorId(uid: String, id: String): Int {
        if (filas.value[id]?.uidPropietario != uid) return 0
        filas.update { it - id }
        return 1
    }

    override suspend fun eliminarDePropietario(uid: String): Int {
        val ids = de(uid).map { it.id }
        filas.update { it - ids.toSet() }
        return ids.size
    }

    override suspend fun eliminarDeOtrosPropietarios(uid: String): Int {
        val ids = filas.value.values.filter { it.uidPropietario != uid }.map { it.id }
        filas.update { it - ids.toSet() }
        return ids.size
    }

    override suspend fun marcarEstadoSync(
        uid: String,
        id: String,
        fechaActualizacion: Long,
        estadoSync: EstadoSync,
        errorSync: String?
    ): Int {
        val actual = filas.value[id] ?: return 0
        if (actual.uidPropietario != uid || actual.fechaActualizacion != fechaActualizacion) return 0
        filas.update { it + (id to actual.copy(estadoSync = estadoSync, errorSync = errorSync)) }
        return 1
    }

    override suspend fun marcarSincronizadosComoPendientes(uid: String): Int {
        var cambiados = 0
        filas.update { mapa ->
            mapa.mapValues { (_, e) ->
                if (e.uidPropietario == uid && e.estadoSync == EstadoSync.SINCRONIZADO) {
                    cambiados++; e.copy(estadoSync = EstadoSync.PENDIENTE_ACTUALIZAR)
                } else e
            }
        }
        return cambiados
    }
}

/**
 * Firestore falso: colecciones users/{uid}/reminders en memoria, set() idempotente por id
 * y contadores de llamadas. Cada llamada demora [demoraMs] (tiempo virtual de runTest) para que
 * las sincronizaciones concurrentes realmente se superpongan.
 */
class FakeFuenteRemota(private val demoraMs: Long = 100) : FuenteRemotaRecordatorios {
    val colecciones = mutableMapOf<String, LinkedHashMap<String, Map<String, Any?>>>()
    val guardadosPorId = mutableMapOf<String, Int>()
    val eliminadosPorId = mutableMapOf<String, Int>()
    val descargasPorUid = mutableMapOf<String, Int>()

    /** Si tiene valor, los set() responden con este error. */
    var errorEnGuardar: ResultadoRemoto<Nothing>? = null

    /** Si tiene valor, TODAS las operaciones responden con este error. */
    var errorGeneral: ResultadoRemoto<Nothing>? = null

    /** Cambios en tiempo real que el test emite a mano. */
    val cambios = MutableSharedFlow<ResultadoRemoto<List<CambioRemoto>>>()

    fun coleccion(uid: String) = colecciones.getOrPut(uid) { LinkedHashMap() }

    /** Carga un documento directamente "en la nube" (por ejemplo, creado desde otro dispositivo). */
    fun sembrar(uid: String, entidad: RecordatorioEntity) {
        coleccion(uid)[entidad.id] = entidad.aMapaFirestore()
    }

    override suspend fun descargar(uid: String): ResultadoRemoto<List<DocumentoRemoto>> {
        delay(demoraMs)
        errorGeneral?.let { return it }
        descargasPorUid[uid] = (descargasPorUid[uid] ?: 0) + 1
        return ResultadoRemoto.Exito(coleccion(uid).map { (id, datos) -> DocumentoRemoto(id, datos) })
    }

    override suspend fun guardar(uid: String, id: String, datos: Map<String, Any?>): ResultadoRemoto<Unit> {
        delay(demoraMs)
        errorGeneral?.let { return it }
        guardadosPorId[id] = (guardadosPorId[id] ?: 0) + 1
        errorEnGuardar?.let { return it }
        coleccion(uid)[id] = datos
        return ResultadoRemoto.Exito(Unit)
    }

    override suspend fun eliminar(uid: String, id: String): ResultadoRemoto<Unit> {
        delay(demoraMs)
        errorGeneral?.let { return it }
        eliminadosPorId[id] = (eliminadosPorId[id] ?: 0) + 1
        coleccion(uid).remove(id)
        return ResultadoRemoto.Exito(Unit)
    }

    override fun observarCambios(uid: String): Flow<ResultadoRemoto<List<CambioRemoto>>> = cambios
}

/** Autenticación falsa: la sesión se cambia a mano desde el test. */
class FakeAuthRepository : AuthRepository {
    override val sesion = MutableStateFlow<EstadoSesion>(EstadoSesion.Verificando)
    var cierres = 0
    var invalidaciones = 0

    fun iniciar(uid: String) {
        sesion.value = EstadoSesion.Activa(Usuario(uid, "$uid@ejemplo.com"))
    }

    override suspend fun registrar(email: String, contrasena: String) =
        ResultadoAuth.Exito(Usuario("nuevo", email))

    override suspend fun iniciarSesion(email: String, contrasena: String) =
        ResultadoAuth.Exito(Usuario("uid-$email", email))

    override suspend fun cerrarSesion() {
        cierres++
        sesion.value = EstadoSesion.SinSesion
    }

    override suspend fun verificarSesion() = Unit

    override suspend fun invalidarSesion(motivo: String) {
        invalidaciones++
        sesion.value = EstadoSesion.Invalida(motivo)
    }
}

/** Entidad de prueba válida. */
fun entidad(
    id: String,
    estadoSync: EstadoSync = EstadoSync.SINCRONIZADO,
    titulo: String = "Recordatorio $id",
    fecha: Long = 1_000L,
    uid: String = "yo"
) = RecordatorioEntity(
    id = id,
    uidPropietario = uid,
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

/**
 * Ejecuta el trabajo pendiente del repositorio, que corre en backgroundScope.
 * (advanceUntilIdle no espera a las corrutinas de backgroundScope.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.procesarSegundoPlano() {
    advanceTimeBy(60_000)
    runCurrent()
}
