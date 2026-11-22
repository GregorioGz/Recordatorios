package com.example.recordatorios

import com.example.recordatorios.data.RecordatoriosRepositoryLocalFirst
import com.example.recordatorios.data.remote.ResultadoRemoto
import com.example.recordatorios.fakes.FakeAuthRepository
import com.example.recordatorios.fakes.FakeFuenteRemota
import com.example.recordatorios.fakes.FakeRecordatorioDao
import com.example.recordatorios.fakes.entidad
import com.example.recordatorios.model.EstadoSesion
import com.example.recordatorios.model.EstadoSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import com.example.recordatorios.fakes.procesarSegundoPlano
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sincronización local-first con fakes del DAO, de Firestore y de Auth (sin emulador). */
@OptIn(ExperimentalCoroutinesApi::class)
class SincronizacionTest {

    private val dao = FakeRecordatorioDao()
    private val remoto = FakeFuenteRemota()
    private val auth = FakeAuthRepository()

    /** Repositorio con la sesión "yo" activa y la sincronización inicial ya terminada. */
    private fun TestScope.repositorioConSesion(): RecordatoriosRepositoryLocalFirst {
        val repo = RecordatoriosRepositoryLocalFirst(
            dao = dao, remoto = remoto, auth = auth, alcanceExterno = backgroundScope,
            escucharEnTiempoReal = false, reloj = { 5_000L }
        )
        auth.iniciar("yo")
        procesarSegundoPlano()
        return repo
    }

    @Test
    fun dosSincronizacionesConcurrentesEnvianCadaPendienteUnaSolaVez() = runTest {
        val repo = repositorioConSesion()
        listOf("a", "b", "c").forEach { dao.insertar(entidad(it, EstadoSync.PENDIENTE_CREAR)) }

        // Dos disparadores al mismo tiempo (por ejemplo, reconexión + botón "Sincronizar ahora")
        listOf(launch { repo.sincronizar() }, launch { repo.sincronizar() }).joinAll()

        assertEquals(mapOf("a" to 1, "b" to 1, "c" to 1), remoto.guardadosPorId)
        assertEquals(3, remoto.coleccion("yo").size)
        assertEquals(3, dao.filas.value.size)
        assertTrue(dao.filas.value.values.all { it.estadoSync == EstadoSync.SINCRONIZADO })
    }

    @Test
    fun reintentarElMismoRegistroNoDuplicaEnFirestore() = runTest {
        val repo = repositorioConSesion()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))
        repo.sincronizar()
        // Se vuelve a marcar como pendiente y se reenvía: set() idempotente sobre el mismo id
        dao.marcarSincronizadosComoPendientes("yo")
        repo.sincronizar()

        assertEquals(2, remoto.guardadosPorId["a"])
        assertEquals(1, remoto.coleccion("yo").size)
        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun timeoutDejaPendienteYElReintentoConfirmaLaMismaFila() = runTest {
        val repo = repositorioConSesion()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))
        remoto.errorEnGuardar = ResultadoRemoto.Timeout

        repo.sincronizar()
        assertEquals(EstadoSync.PENDIENTE_CREAR, dao.filas.value.getValue("a").estadoSync)
        assertNotNull(dao.filas.value.getValue("a").errorSync)

        remoto.errorEnGuardar = null
        repo.sincronizar()
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.getValue("a").estadoSync)
        assertNull(dao.filas.value.getValue("a").errorSync)
        assertEquals(1, remoto.coleccion("yo").size)
        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun sinConexionElRegistroSiguePendienteYSeEnviaAlVolverLaRed() = runTest {
        val repo = repositorioConSesion()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_ACTUALIZAR))
        remoto.errorGeneral = ResultadoRemoto.SinConexion

        repo.sincronizar()
        assertEquals(EstadoSync.PENDIENTE_ACTUALIZAR, dao.filas.value.getValue("a").estadoSync)

        remoto.errorGeneral = null
        repo.sincronizar()
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.getValue("a").estadoSync)
        assertEquals(1, remoto.coleccion("yo").size)
    }

    @Test
    fun permisoDenegadoDejaElRegistroEnErrorConAccesoDenegado() = runTest {
        val repo = repositorioConSesion()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR, titulo = "Dato local"))
        remoto.errorEnGuardar = ResultadoRemoto.SinPermiso

        repo.sincronizar()

        val fila = dao.filas.value.getValue("a")
        assertEquals(EstadoSync.ERROR, fila.estadoSync)
        assertEquals("Acceso denegado", fila.errorSync)
        assertEquals("Dato local", fila.titulo)   // el dato local nunca se pierde
    }

    @Test
    fun noAutenticadoInvalidaLaSesionYLimpiaLosDatos() = runTest {
        val repo = repositorioConSesion()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))
        remoto.errorEnGuardar = ResultadoRemoto.SesionInvalida

        repo.sincronizar()
        procesarSegundoPlano()

        assertEquals(1, auth.invalidaciones)
        assertTrue(auth.sesion.value is EstadoSesion.Invalida)
        assertTrue(dao.filas.value.isEmpty())
    }

    @Test
    fun eliminarAlgoNuncaSincronizadoLoBorraSinLlamarALaNube() = runTest {
        val repo = repositorioConSesion()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))

        repo.eliminar("a")

        assertFalse("a" in dao.filas.value)
        assertTrue(remoto.eliminadosPorId.isEmpty())
    }

    @Test
    fun eliminarAlgoSincronizadoLoOcultaYLoBorraAlConfirmar() = runTest {
        val repo = repositorioConSesion()
        dao.insertar(entidad("a", EstadoSync.SINCRONIZADO))
        remoto.sembrar("yo", entidad("a"))

        repo.eliminar("a")
        assertEquals(EstadoSync.PENDIENTE_ELIMINAR, dao.filas.value.getValue("a").estadoSync)
        procesarSegundoPlano()   // la eliminación dispara la sincronización en segundo plano

        assertFalse("a" in dao.filas.value)
        assertEquals(1, remoto.eliminadosPorId["a"])
        assertTrue(remoto.coleccion("yo").isEmpty())
    }

    @Test
    fun laDescargaNoPisaPendientesYDescartaDocumentosInvalidos() = runTest {
        val repo = repositorioConSesion()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_ACTUALIZAR, titulo = "Versión local"))
        remoto.sembrar("yo", entidad("a", titulo = "Versión remota"))
        remoto.sembrar("yo", entidad("b", titulo = "Nuevo en la nube"))
        remoto.coleccion("yo")["roto"] = mapOf("id" to "roto", "titulo" to "ab")   // inválido
        remoto.errorEnGuardar = ResultadoRemoto.ErrorDesconocido("rechazado")       // la subida falla, la descarga no

        repo.sincronizar()

        assertEquals("Versión local", dao.filas.value.getValue("a").titulo)
        assertEquals(EstadoSync.ERROR, dao.filas.value.getValue("a").estadoSync)
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.getValue("b").estadoSync)
        assertFalse("roto" in dao.filas.value)
        assertEquals(2, dao.filas.value.size)
    }
}
