package com.example.recordatorios

import com.example.recordatorios.data.RecordatoriosRepositoryLocalFirst
import com.example.recordatorios.data.remote.RecordatorioDto
import com.example.recordatorios.fakes.FakeRecordatorioDao
import com.example.recordatorios.fakes.FakeRecordatoriosApi
import com.example.recordatorios.fakes.entidad
import com.example.recordatorios.model.EstadoSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/** Sincronización local-first con fakes del DAO y de la API (sin emulador). */
@OptIn(ExperimentalCoroutinesApi::class)
class SincronizacionTest {

    private val dao = FakeRecordatorioDao()
    private val api = FakeRecordatoriosApi()

    private fun TestScope.repositorio() =
        RecordatoriosRepositoryLocalFirst(dao = dao, api = api, alcanceExterno = backgroundScope, reloj = { 5_000L })

    @Test
    fun dosSincronizacionesConcurrentesEnvianCadaPendienteUnaSolaVez() = runTest {
        listOf("a", "b", "c").forEach { dao.insertar(entidad(it, EstadoSync.PENDIENTE_CREAR)) }
        val repo = repositorio()

        // Dos disparadores al mismo tiempo (por ejemplo, reconexión + botón "Sincronizar ahora")
        listOf(launch { repo.sincronizar() }, launch { repo.sincronizar() }).joinAll()

        assertEquals(mapOf("a" to 1, "b" to 1, "c" to 1), api.putPorId)
        assertEquals(3, api.servidor.size)
        assertEquals(3, dao.filas.value.size)
        assertTrue(dao.filas.value.values.all { it.estadoSync == EstadoSync.SINCRONIZADO })
    }

    @Test
    fun reenviarElMismoRegistroNoDuplicaEnElServidor() = runTest {
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))
        val repo = repositorio()
        repo.sincronizar()
        // Se vuelve a marcar como pendiente y se reenvía: PUT idempotente por id
        dao.marcarSincronizadosComoPendientes()
        repo.sincronizar()

        assertEquals(2, api.putPorId["a"])
        assertEquals(1, api.servidor.size)
        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun error500DejaElRegistroEnErrorYConservaLosDatos() = runTest {
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR, titulo = "Dato local"))
        api.codigoErrorEnPut = 500

        repositorio().sincronizar()

        val fila = dao.filas.value.getValue("a")
        assertEquals(EstadoSync.ERROR, fila.estadoSync)
        assertEquals("Dato local", fila.titulo)
        assertNotNull(fila.errorSync)
    }

    @Test
    fun sinConexionElRegistroSiguePendiente() = runTest {
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))
        api.excepcion = UnknownHostException("sin red")
        val repo = repositorio()

        repo.sincronizar()

        val fila = dao.filas.value.getValue("a")
        assertEquals(EstadoSync.PENDIENTE_CREAR, fila.estadoSync)
        assertNotNull(fila.errorSync)

        // Vuelve la red: el mismo registro se confirma sin crear otro
        api.excepcion = null
        repo.sincronizar()
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.getValue("a").estadoSync)
        assertNull(dao.filas.value.getValue("a").errorSync)
        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun eliminarAlgoNuncaSincronizadoLoBorraSinLlamarAlServidor() = runTest {
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))
        val repo = repositorio()

        repo.eliminar("a")

        assertFalse("a" in dao.filas.value)
        assertTrue(api.deletePorId.isEmpty())
    }

    @Test
    fun eliminarAlgoSincronizadoLoOcultaYLoBorraAlConfirmar() = runTest {
        dao.insertar(entidad("a", EstadoSync.SINCRONIZADO))
        api.servidor["a"] = RecordatorioDto("a", "Recordatorio a", null, 1, "Trabajo", "ALTA", "PENDIENTE", fechaCreacion = 1_000, fechaActualizacion = 1_000)
        val repo = repositorio()

        repo.eliminar("a")
        assertEquals(EstadoSync.PENDIENTE_ELIMINAR, dao.filas.value.getValue("a").estadoSync)

        repo.sincronizar()
        assertFalse("a" in dao.filas.value)
        assertEquals(1, api.deletePorId["a"])
        assertTrue(api.servidor.isEmpty())
    }

    @Test
    fun laDescargaNoPisaCambiosLocalesPendientes() = runTest {
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_ACTUALIZAR, titulo = "Versión local"))
        api.servidor["a"] = RecordatorioDto("a", "Versión remota", null, 1, "Trabajo", "ALTA", "PENDIENTE", fechaCreacion = 1_000, fechaActualizacion = 900)
        api.servidor["b"] = RecordatorioDto("b", "Nuevo en el servidor", null, 2, "Estudio", "BAJA", "PENDIENTE", fechaCreacion = 2_000, fechaActualizacion = 2_000)
        api.codigoErrorEnPut = 400 // la subida falla, la descarga sí funciona

        repositorio().sincronizar()

        assertEquals("Versión local", dao.filas.value.getValue("a").titulo)
        assertEquals(EstadoSync.ERROR, dao.filas.value.getValue("a").estadoSync)
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.getValue("b").estadoSync)
        assertEquals(2, dao.filas.value.size)
    }
}
