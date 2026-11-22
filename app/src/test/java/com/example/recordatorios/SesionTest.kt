package com.example.recordatorios

import com.example.recordatorios.data.RecordatoriosRepositoryLocalFirst
import com.example.recordatorios.data.ResultadoCierreSesion
import com.example.recordatorios.data.remote.CambioRemoto
import com.example.recordatorios.data.remote.ResultadoRemoto
import com.example.recordatorios.fakes.FakeAuthRepository
import com.example.recordatorios.fakes.FakeFuenteRemota
import com.example.recordatorios.fakes.FakeRecordatorioDao
import com.example.recordatorios.fakes.entidad
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.EstadoSesion
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.data.DatosSimulados
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import com.example.recordatorios.fakes.procesarSegundoPlano
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aislamiento por cuenta: qué ve cada sesión y qué se borra al cerrarla. */
@OptIn(ExperimentalCoroutinesApi::class)
class SesionTest {

    private val dao = FakeRecordatorioDao()
    private val remoto = FakeFuenteRemota()
    private val auth = FakeAuthRepository()

    private fun TestScope.repositorio(escuchar: Boolean = false) = RecordatoriosRepositoryLocalFirst(
        dao = dao, remoto = remoto, auth = auth, alcanceExterno = backgroundScope,
        escucharEnTiempoReal = escuchar, reloj = { 5_000L }
    )

    private val borrador = BorradorRecordatorio("Llamar al banco", null, DatosSimulados.trabajo, Prioridad.Alta)

    @Test
    fun soloSeExponenLosDatosDelUidActivo() = runTest {
        dao.insertar(entidad("mio", uid = "yo"))
        remoto.sembrar("yo", entidad("mio"))
        dao.insertar(entidad("ajeno", uid = "otra"))
        dao.insertar(entidad("migrado", uid = ""))   // fila sin dueño de la versión 1
        val repo = repositorio()

        auth.iniciar("yo")
        procesarSegundoPlano()

        assertEquals(listOf("mio"), repo.observarRecordatorios().first().map { it.id })
        assertNull(repo.observarRecordatorio("ajeno").first())
        // Defensa adicional: al iniciar sesión se descartan las filas de otras cuentas y las sin dueño
        assertEquals(setOf("mio"), dao.filas.value.keys)
    }

    @Test
    fun sinSesionNoSeExponeNadaNiSePuedeEscribir() = runTest {
        dao.insertar(entidad("a", uid = "yo"))
        val repo = repositorio()
        auth.sesion.value = EstadoSesion.SinSesion
        procesarSegundoPlano()

        assertTrue(repo.observarRecordatorios().first().isEmpty())
        val fallo = runCatching { repo.crear(borrador) }.exceptionOrNull()
        assertTrue(fallo is IllegalStateException)
    }

    @Test
    fun loCreadoQuedaAsociadoAlUidDeLaSesion() = runTest {
        val repo = repositorio()
        auth.iniciar("yo")
        procesarSegundoPlano()

        val creado = repo.crear(borrador)
        procesarSegundoPlano()

        assertEquals("yo", dao.filas.value.getValue(creado.id).uidPropietario)
        assertTrue(creado.id in remoto.coleccion("yo"))
        assertTrue(remoto.coleccion("otra").isEmpty())
    }

    @Test
    fun cerrarSesionBorraLosDatosLocalesDeLaCuenta() = runTest {
        val repo = repositorio()
        auth.iniciar("yo")
        procesarSegundoPlano()
        repo.crear(borrador)
        procesarSegundoPlano()

        val resultado = repo.cerrarSesion(sincronizarAntes = false)
        procesarSegundoPlano()

        assertEquals(ResultadoCierreSesion.Cerrada, resultado)
        assertTrue(dao.filas.value.isEmpty())
        assertEquals(1, auth.cierres)
        assertTrue(repo.observarRecordatorios().first().isEmpty())
        // Lo sincronizado sigue en la nube: se recupera al volver a iniciar sesión
        assertEquals(1, remoto.coleccion("yo").size)
    }

    @Test
    fun sincronizarYSalirSinRedNoCierraLaSesionNiBorraPendientes() = runTest {
        val repo = repositorio()
        auth.iniciar("yo")
        procesarSegundoPlano()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))
        remoto.errorGeneral = ResultadoRemoto.SinConexion

        val resultado = repo.cerrarSesion(sincronizarAntes = true)

        assertTrue(resultado is ResultadoCierreSesion.QuedanPendientes)
        assertEquals(1, (resultado as ResultadoCierreSesion.QuedanPendientes).cantidad)
        assertEquals(0, auth.cierres)
        assertTrue("a" in dao.filas.value)
    }

    @Test
    fun alIniciarSesionSeRecuperanLosDatosDeLaNube() = runTest {
        // Datos creados desde otro dispositivo (o antes de reinstalar)
        remoto.sembrar("yo", entidad("x", titulo = "Desde otro dispositivo"))
        remoto.sembrar("otra", entidad("y", uid = "otra"))
        val repo = repositorio()

        auth.iniciar("yo")
        procesarSegundoPlano()

        val visibles = repo.observarRecordatorios().first()
        assertEquals(listOf("Desde otro dispositivo"), visibles.map { it.titulo })
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.getValue("x").estadoSync)
    }

    @Test
    fun cambiarDeCuentaNoMuestraDatosDeLaAnterior() = runTest {
        val repo = repositorio()
        auth.iniciar("yo")
        procesarSegundoPlano()
        repo.crear(borrador)
        procesarSegundoPlano()

        repo.cerrarSesion(sincronizarAntes = false)
        auth.iniciar("otra")
        procesarSegundoPlano()

        assertTrue(repo.observarRecordatorios().first().isEmpty())
        assertTrue(dao.filas.value.values.none { it.uidPropietario == "yo" })
    }

    @Test
    fun sesionInvalidaLimpiaLosDatosLocales() = runTest {
        repositorio()
        auth.iniciar("yo")
        procesarSegundoPlano()
        dao.insertar(entidad("a", EstadoSync.PENDIENTE_CREAR))

        auth.invalidarSesion("Tu sesión venció")
        procesarSegundoPlano()

        assertTrue(dao.filas.value.isEmpty())
    }

    @Test
    fun unBorradoHechoEnOtroDispositivoLlegaEnTiempoReal() = runTest {
        remoto.sembrar("yo", entidad("x"))
        remoto.sembrar("yo", entidad("z"))
        val repo = repositorio(escuchar = true)
        auth.iniciar("yo")
        procesarSegundoPlano()
        dao.upsert(entidad("z", EstadoSync.PENDIENTE_ACTUALIZAR, titulo = "Editado acá"))

        remoto.cambios.emit(ResultadoRemoto.Exito(listOf(CambioRemoto.Eliminado("x"), CambioRemoto.Eliminado("z"))))
        procesarSegundoPlano()

        assertFalse("x" in dao.filas.value)                       // estaba sincronizado: se borra
        assertEquals("Editado acá", dao.filas.value.getValue("z").titulo) // tenía cambios locales: se conserva
        assertEquals(listOf("z"), repo.observarRecordatorios().first().map { it.id })
    }
}
