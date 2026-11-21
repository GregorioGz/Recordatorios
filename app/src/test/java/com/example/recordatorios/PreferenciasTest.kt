package com.example.recordatorios

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.example.recordatorios.data.preferencias.ClavesPreferencias
import com.example.recordatorios.data.preferencias.FiltroPrioridad
import com.example.recordatorios.data.preferencias.Orden
import com.example.recordatorios.data.preferencias.PreferenciasRepositoryDataStore
import com.example.recordatorios.data.preferencias.PreferenciasUsuario
import com.example.recordatorios.data.preferencias.Tema
import com.example.recordatorios.data.preferencias.aPreferenciasUsuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Lectura de preferencias: valores guardados, ausentes, inválidos y error de lectura. */
class PreferenciasTest {

    @Test
    fun sinDatosGuardadosSeUsanLosPredeterminados() {
        assertEquals(PreferenciasUsuario(), emptyPreferences().aPreferenciasUsuario())
    }

    @Test
    fun valoresValidosSeLeenCorrectamente() {
        val guardadas = preferencesOf(
            ClavesPreferencias.TEMA to "OSCURO",
            ClavesPreferencias.MOSTRAR_COMPLETADOS to false,
            ClavesPreferencias.ORDEN to "PRIORIDAD",
            ClavesPreferencias.FILTRO_PRIORIDAD to "ALTA"
        )
        assertEquals(
            PreferenciasUsuario(Tema.OSCURO, false, Orden.PRIORIDAD, FiltroPrioridad.ALTA),
            guardadas.aPreferenciasUsuario()
        )
    }

    @Test
    fun temaInvalidoDevuelveElPredeterminadoSinAfectarLoDemas() {
        val guardadas = preferencesOf(
            ClavesPreferencias.TEMA to PreferenciasRepositoryDataStore.VALOR_TEMA_INVALIDO,
            ClavesPreferencias.ORDEN to "TITULO"
        )
        val leidas = guardadas.aPreferenciasUsuario()
        assertEquals(Tema.SISTEMA, leidas.tema)
        assertEquals(Orden.TITULO, leidas.orden)
    }

    @Test
    fun ordenYFiltroInvalidosDevuelvenLosPredeterminados() {
        val leidas = preferencesOf(
            ClavesPreferencias.ORDEN to "ALEATORIO",
            ClavesPreferencias.FILTRO_PRIORIDAD to ""
        ).aPreferenciasUsuario()
        assertEquals(Orden.FECHA, leidas.orden)
        assertEquals(FiltroPrioridad.TODAS, leidas.filtroPrioridad)
    }

    @Test
    fun errorDeLecturaRecuperableDevuelveLosPredeterminados() = runTest {
        val dataStoreQueFalla = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("archivo dañado") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) =
                throw IOException("no se puede escribir")
        }
        val leidas = PreferenciasRepositoryDataStore(dataStoreQueFalla).preferencias.first()
        assertEquals(PreferenciasUsuario(), leidas)
    }
}
