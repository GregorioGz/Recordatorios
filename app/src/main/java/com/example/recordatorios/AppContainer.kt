package com.example.recordatorios

import android.content.Context
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.data.RecordatoriosRepositoryLocalFirst
import com.example.recordatorios.data.SimuladorDeEscenarios
import com.example.recordatorios.data.local.AppDatabase
import com.example.recordatorios.data.preferencias.PreferenciasRepository
import com.example.recordatorios.data.preferencias.PreferenciasRepositoryDataStore
import com.example.recordatorios.data.preferencias.preferenciasDataStore
import com.example.recordatorios.data.remote.ClienteRemoto
import com.example.recordatorios.data.remote.MonitorDeConectividad
import com.example.recordatorios.data.remote.ServidorSimuladoInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Contenedor de dependencias simple (inyección manual).
 * Hay UNA sola instancia de cada fuente de datos (DataStore, base Room, cliente
 * Retrofit, repositorio), compartida por todos los ViewModel.
 * Se inicializa desde RecordatoriosApp con el contexto de la aplicación
 * (nunca con una Activity, para no retenerla en memoria).
 */
object AppContainer {
    private lateinit var contextoApp: Context

    /**
     * Alcance de la aplicación: el trabajo lanzado acá (sincronización) no se
     * cancela al cerrar una pantalla. SupervisorJob: un fallo no cancela al resto.
     */
    private val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun inicializar(context: Context) {
        contextoApp = context.applicationContext
        // Al iniciar y cada vez que vuelve la red, se sincroniza (protegido por el Mutex del repositorio)
        repositorioLocalFirst.observarConectividad(MonitorDeConectividad(contextoApp).hayConexion)
    }

    private val baseDeDatos: AppDatabase by lazy { AppDatabase.obtener(contextoApp) }

    /** Backend en memoria: solo se usa si la URL base es la de ejemplo. */
    private val servidorSimulado: ServidorSimuladoInterceptor? by lazy {
        if (ClienteRemoto.usaServidorSimulado(BuildConfig.API_BASE_URL)) {
            ServidorSimuladoInterceptor(ClienteRemoto.json)
        } else null
    }

    private val repositorioLocalFirst by lazy {
        RecordatoriosRepositoryLocalFirst(
            dao = baseDeDatos.recordatorioDao(),
            api = ClienteRemoto.crearApi(
                urlBase = BuildConfig.API_BASE_URL,
                registrarTrafico = BuildConfig.DEBUG,
                servidorSimulado = servidorSimulado
            ),
            alcanceExterno = alcanceApp,
            servidorSimulado = servidorSimulado
        )
    }

    // Los ViewModel reciben estas referencias tipadas como INTERFACES
    val repositorio: RecordatoriosRepository get() = repositorioLocalFirst
    val simulador: SimuladorDeEscenarios get() = repositorioLocalFirst

    val preferencias: PreferenciasRepository by lazy {
        PreferenciasRepositoryDataStore(contextoApp.preferenciasDataStore)
    }
}
