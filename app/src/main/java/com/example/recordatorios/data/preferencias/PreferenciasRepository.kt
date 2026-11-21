package com.example.recordatorios.data.preferencias

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * ÚNICA instancia de Preferences DataStore de la app. El delegado a nivel de
 * archivo garantiza un solo DataStore por nombre de archivo en el proceso;
 * solo lo consume AppContainer.
 */
val Context.preferenciasDataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias_usuario")

/** Claves TIPADAS: el tipo de cada valor queda fijado por la clave. */
object ClavesPreferencias {
    val TEMA = stringPreferencesKey("tema")
    val MOSTRAR_COMPLETADOS = booleanPreferencesKey("mostrar_completados")
    val ORDEN = stringPreferencesKey("orden")
    val FILTRO_PRIORIDAD = stringPreferencesKey("filtro_prioridad")
}

/** Contrato que usan los ViewModel (no conocen DataStore). */
interface PreferenciasRepository {
    val preferencias: Flow<PreferenciasUsuario>
    suspend fun cambiarTema(tema: Tema)
    suspend fun cambiarMostrarCompletados(mostrar: Boolean)
    suspend fun cambiarOrden(orden: Orden)
    suspend fun cambiarFiltroPrioridad(filtro: FiltroPrioridad)

    /** SOLO PARA PRUEBAS: guarda un texto que no corresponde a ningún Tema. */
    suspend fun guardarTemaInvalidoParaPruebas()
}

class PreferenciasRepositoryDataStore(
    private val dataStore: DataStore<Preferences>
) : PreferenciasRepository {

    override val preferencias: Flow<PreferenciasUsuario> = dataStore.data
        .catch { error ->
            // Error de lectura RECUPERABLE (archivo dañado o ilegible): se usan los predeterminados
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.aPreferenciasUsuario() }
        .distinctUntilChanged()

    // Las escrituras son suspend: edit() trabaja en su propio dispatcher de IO
    override suspend fun cambiarTema(tema: Tema) {
        dataStore.edit { it[ClavesPreferencias.TEMA] = tema.clave }
    }

    override suspend fun cambiarMostrarCompletados(mostrar: Boolean) {
        dataStore.edit { it[ClavesPreferencias.MOSTRAR_COMPLETADOS] = mostrar }
    }

    override suspend fun cambiarOrden(orden: Orden) {
        dataStore.edit { it[ClavesPreferencias.ORDEN] = orden.clave }
    }

    override suspend fun cambiarFiltroPrioridad(filtro: FiltroPrioridad) {
        dataStore.edit { it[ClavesPreferencias.FILTRO_PRIORIDAD] = filtro.clave }
    }

    override suspend fun guardarTemaInvalidoParaPruebas() {
        dataStore.edit { it[ClavesPreferencias.TEMA] = VALOR_TEMA_INVALIDO }
    }

    companion object {
        const val VALOR_TEMA_INVALIDO = "VIOLETA_FOSFORESCENTE"
    }
}

/**
 * Mapeo Preferences -> modelo propio. Es una función pura (fácil de testear):
 * clave ausente o valor desconocido -> valor predeterminado.
 */
fun Preferences.aPreferenciasUsuario(): PreferenciasUsuario {
    val predeterminadas = PreferenciasUsuario()
    return PreferenciasUsuario(
        tema = Tema.desdeClave(this[ClavesPreferencias.TEMA]),
        mostrarCompletados = this[ClavesPreferencias.MOSTRAR_COMPLETADOS] ?: predeterminadas.mostrarCompletados,
        orden = Orden.desdeClave(this[ClavesPreferencias.ORDEN]),
        filtroPrioridad = FiltroPrioridad.desdeClave(this[ClavesPreferencias.FILTRO_PRIORIDAD])
    )
}
