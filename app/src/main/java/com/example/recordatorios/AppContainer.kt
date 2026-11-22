package com.example.recordatorios

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.data.RecordatoriosRepositoryLocalFirst
import com.example.recordatorios.data.SimuladorDeEscenarios
import com.example.recordatorios.data.auth.AuthRepository
import com.example.recordatorios.data.auth.AuthRepositoryFirebase
import com.example.recordatorios.data.local.AppDatabase
import com.example.recordatorios.data.preferencias.PreferenciasRepository
import com.example.recordatorios.data.preferencias.PreferenciasRepositoryDataStore
import com.example.recordatorios.data.preferencias.preferenciasDataStore
import com.example.recordatorios.data.remote.FuenteRemotaFirestore
import com.example.recordatorios.data.remote.MonitorDeConectividad
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Contenedor de dependencias simple (inyección manual).
 * Hay UNA sola instancia de cada fuente de datos (DataStore, base Room, Firebase,
 * repositorios), compartida por todos los ViewModel.
 * Se inicializa desde RecordatoriosApp con el contexto de la aplicación
 * (nunca con una Activity, para no retenerla en memoria).
 */
object AppContainer {
    private lateinit var contextoApp: Context

    /**
     * Alcance de la aplicación: el trabajo lanzado acá (sesión, sincronización) no se
     * cancela al cerrar una pantalla. SupervisorJob: un fallo no cancela al resto.
     */
    private val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun inicializar(context: Context) {
        contextoApp = context.applicationContext
        // Al iniciar y cada vez que vuelve la red, se sincroniza (protegido por el Mutex del repositorio)
        repositorioLocalFirst.observarConectividad(MonitorDeConectividad(contextoApp).hayConexion)
        // Al iniciar y cada vez que la app vuelve al primer plano, se comprueba que la sesión siga siendo válida
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                alcanceApp.launch { auth.verificarSesion() }
            }
        })
    }

    private val baseDeDatos: AppDatabase by lazy { AppDatabase.obtener(contextoApp) }

    /** Firestore con caché en memoria: Room es la única caché persistente. */
    private val fuenteRemota by lazy { FuenteRemotaFirestore(FuenteRemotaFirestore.crearInstancia()) }

    val auth: AuthRepository by lazy {
        AuthRepositoryFirebase(FirebaseAuth.getInstance(), alcanceApp)
    }

    private val repositorioLocalFirst by lazy {
        RecordatoriosRepositoryLocalFirst(
            dao = baseDeDatos.recordatorioDao(),
            remoto = fuenteRemota,
            auth = auth,
            alcanceExterno = alcanceApp,
            // La prueba de acceso a otra cuenta solo existe en el flavor dev
            pruebaAutorizacion = fuenteRemota.takeIf { BuildConfig.HERRAMIENTAS_DE_PRUEBA }
        )
    }

    // Los ViewModel reciben estas referencias tipadas como INTERFACES
    val repositorio: RecordatoriosRepository get() = repositorioLocalFirst
    val simulador: SimuladorDeEscenarios get() = repositorioLocalFirst

    val preferencias: PreferenciasRepository by lazy {
        PreferenciasRepositoryDataStore(contextoApp.preferenciasDataStore)
    }
}
