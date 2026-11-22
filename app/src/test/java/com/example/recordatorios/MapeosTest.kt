package com.example.recordatorios

import com.example.recordatorios.data.CamposFirestore
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.data.aDominio
import com.example.recordatorios.data.aEntity
import com.example.recordatorios.data.aEntityNueva
import com.example.recordatorios.data.aMapaFirestore
import com.example.recordatorios.data.conCambios
import com.example.recordatorios.data.local.ConvertidoresRoom
import com.example.recordatorios.data.remote.DocumentoRemoto
import com.example.recordatorios.fakes.entidad
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Prioridad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Mapeos explícitos Dominio <-> Entity <-> Map de Firestore. */
class MapeosTest {

    private val enviado = entidad("id-1", EstadoSync.PENDIENTE_CREAR, titulo = "Comprar pan").copy(
        descripcion = "Integral", categoriaId = 4, categoriaNombre = "Hogar", prioridad = "BAJA",
        estado = "EN_CURSO", fechaCreacion = 10, fechaActualizacion = 20
    )

    private fun documento(cambios: Map<String, Any?> = emptyMap(), id: String = "id-1") =
        DocumentoRemoto(id, enviado.aMapaFirestore() + cambios)

    @Test
    fun dominioAEntityYDeVueltaConservaTodosLosDatos() {
        DatosSimulados.recordatorios.forEach { original ->
            assertEquals(original, original.aEntity("yo").aDominio())
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
    fun elMapaDeFirestoreTieneSoloLosCamposDelContrato() {
        val mapa = enviado.aMapaFirestore()
        assertEquals(
            setOf("id", "titulo", "descripcion", "categoriaId", "categoriaNombre", "prioridad", "estado",
                "fechaCreacion", "fechaActualizacion"),
            mapa.keys
        )
        assertEquals("id-1", mapa[CamposFirestore.ID])
        assertEquals(4L, mapa[CamposFirestore.CATEGORIA_ID])   // Firestore guarda enteros como Long
        assertFalse("uidPropietario" in mapa)                     // el uid va en la ruta, no en el documento
        assertFalse("estadoSync" in mapa)                         // dato solo local
    }

    @Test
    fun entityAMapaYDeVueltaConservaLosDatosYQuedaSincronizado() {
        val recibido = documento().aEntity("yo")
        assertEquals(enviado.copy(estadoSync = EstadoSync.SINCRONIZADO), recibido)
    }

    @Test
    fun losNumerosDeFirestoreSeAceptanComoLongODouble() {
        val recibido = documento(mapOf("categoriaId" to 4.0, "fechaCreacion" to 10L)).aEntity("yo")
        assertNotNull(recibido)
        assertEquals(4, recibido!!.categoriaId)
    }

    @Test
    fun documentosInvalidosSeDescartan() {
        assertNull(documento(mapOf("titulo" to "ab")).aEntity("yo"))                 // título corto
        assertNull(documento(mapOf("titulo" to "x".repeat(61))).aEntity("yo"))      // título largo
        assertNull(documento(mapOf("descripcion" to "x".repeat(201))).aEntity("yo"))
        assertNull(documento(mapOf("descripcion" to 5)).aEntity("yo"))              // tipo incorrecto
        assertNull(documento(mapOf("prioridad" to "URGENTISIMA")).aEntity("yo"))
        assertNull(documento(mapOf("estado" to "ARCHIVADO")).aEntity("yo"))
        assertNull(documento(mapOf("fechaCreacion" to null)).aEntity("yo"))
        assertNull(documento(id = "otro-id").aEntity("yo"))                         // id del campo != id del documento
    }

    @Test
    fun borradorAEntityNuevaQuedaPendienteDeCrearYConDuenio() {
        val borrador = BorradorRecordatorio("Llamar al banco", null, DatosSimulados.trabajo, Prioridad.Alta)
        val entity = borrador.aEntityNueva(id = "uuid", uidPropietario = "yo", ahora = 1_234)
        assertEquals("uuid", entity.id)
        assertEquals("yo", entity.uidPropietario)
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
        assertEquals(original.uidPropietario, editado.uidPropietario)
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
