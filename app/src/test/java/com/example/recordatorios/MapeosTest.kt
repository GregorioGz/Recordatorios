package com.example.recordatorios

import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.data.aDominio
import com.example.recordatorios.data.aDto
import com.example.recordatorios.data.aEntity
import com.example.recordatorios.data.aEntityNueva
import com.example.recordatorios.data.conCambios
import com.example.recordatorios.data.esValido
import com.example.recordatorios.data.local.ConvertidoresRoom
import com.example.recordatorios.data.remote.RecordatorioDto
import com.example.recordatorios.fakes.entidad
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Prioridad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mapeos explícitos DTO <-> Entity <-> Dominio. */
class MapeosTest {

    private val dtoValido = RecordatorioDto(
        id = "id-1",
        titulo = "Comprar pan",
        descripcion = "Integral",
        categoriaId = 4,
        categoriaNombre = "Hogar",
        prioridad = "BAJA",
        estado = "EN_CURSO",
        fechaCreacion = 10,
        fechaActualizacion = 20
    )

    @Test
    fun dominioAEntityYDeVueltaConservaTodosLosDatos() {
        DatosSimulados.recordatorios.forEach { original ->
            assertEquals(original, original.aEntity().aDominio())
        }
    }

    @Test
    fun entityADominioConvierteClavesEnTiposDelDominio() {
        val dominio = entidad("x").copy(prioridad = "ALTA", estado = "COMPLETADO").aDominio()
        assertEquals(Prioridad.Alta, dominio.prioridad)
        assertEquals(EstadoRecordatorio.Completado, dominio.estado)
        assertEquals(DatosSimulados.trabajo, dominio.categoria)
    }

    @Test
    fun dtoAEntityYDeVueltaConservaElContrato() {
        val entity = dtoValido.aEntity()
        assertEquals(EstadoSync.SINCRONIZADO, entity.estadoSync)
        assertEquals(dtoValido, entity.aDto())
    }

    @Test
    fun dtoConClavesDesconocidasUsaValoresPredeterminados() {
        val entity = dtoValido.copy(prioridad = "URGENTISIMA", estado = "ARCHIVADO").aEntity()
        assertEquals(Prioridad.Media.clave, entity.prioridad)
        assertEquals(EstadoRecordatorio.Pendiente.clave, entity.estado)
    }

    @Test
    fun dtoInvalidoSeDescarta() {
        assertTrue(dtoValido.esValido())
        assertFalse(dtoValido.copy(titulo = "ab").esValido())
        assertFalse(dtoValido.copy(id = " ").esValido())
    }

    @Test
    fun borradorAEntityNuevaQuedaPendienteDeCrear() {
        val borrador = BorradorRecordatorio("Llamar al banco", null, DatosSimulados.trabajo, Prioridad.Alta)
        val entity = borrador.aEntityNueva(id = "uuid", ahora = 1_234)
        assertEquals("uuid", entity.id)
        assertEquals(EstadoSync.PENDIENTE_CREAR, entity.estadoSync)
        assertEquals(1_234, entity.fechaCreacion)
        assertEquals(1_234, entity.fechaActualizacion)
        assertEquals("PENDIENTE", entity.estado)
    }

    @Test
    fun editarUnSincronizadoLoDejaPendienteDeActualizarEnLaMismaFila() {
        val original = entidad("a", EstadoSync.SINCRONIZADO, fecha = 1_000)
        val borrador = BorradorRecordatorio("Título nuevo", "desc", DatosSimulados.salud, Prioridad.Baja, EstadoRecordatorio.EnCurso)
        val editado = original.conCambios(borrador, ahora = 1_000) // mismo reloj: la versión igual avanza

        assertEquals("a", editado.id)
        assertEquals(original.fechaCreacion, editado.fechaCreacion)
        assertEquals(1_001, editado.fechaActualizacion)
        assertEquals(EstadoSync.PENDIENTE_ACTUALIZAR, editado.estadoSync)
        assertEquals("EN_CURSO", editado.estado)
    }

    @Test
    fun editarAlgoNuncaEnviadoSigueSiendoPendienteDeCrear() {
        val borrador = BorradorRecordatorio("Título nuevo", null, DatosSimulados.salud, Prioridad.Baja)
        val editado = entidad("a", EstadoSync.PENDIENTE_CREAR).conCambios(borrador, ahora = 9_000)
        assertEquals(EstadoSync.PENDIENTE_CREAR, editado.estadoSync)
    }

    @Test
    fun estadoSyncDesconocidoEnRoomSeTrataComoError() {
        val convertidores = ConvertidoresRoom()
        assertEquals(EstadoSync.ERROR, convertidores.textoAEstadoSync("OTRO"))
        assertEquals(EstadoSync.PENDIENTE_ELIMINAR, convertidores.textoAEstadoSync("PENDIENTE_ELIMINAR"))
    }
}
