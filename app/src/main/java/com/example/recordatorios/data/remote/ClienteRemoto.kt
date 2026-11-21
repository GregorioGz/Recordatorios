package com.example.recordatorios.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Construcción de Retrofit + OkHttp.
 * - Timeouts EXPLÍCITOS.
 * - HttpLoggingInterceptor solo en debug.
 * - Si la URL base es la de ejemplo, se agrega el servidor simulado.
 */
object ClienteRemoto {

    /** Debe coincidir con el valor por defecto definido en app/build.gradle.kts. */
    const val URL_DE_EJEMPLO = "https://api.recordatorios.example/"

    private const val TIMEOUT_CONEXION_S = 10L
    private const val TIMEOUT_LECTURA_S = 15L
    private const val TIMEOUT_ESCRITURA_S = 15L

    val json = Json {
        ignoreUnknownKeys = true   // campos nuevos del servidor no rompen la app
        explicitNulls = false
    }

    fun usaServidorSimulado(urlBase: String) = urlBase == URL_DE_EJEMPLO

    fun crearApi(
        urlBase: String,
        registrarTrafico: Boolean,
        servidorSimulado: ServidorSimuladoInterceptor?
    ): RecordatoriosApi {
        val cliente = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_CONEXION_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_LECTURA_S, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_ESCRITURA_S, TimeUnit.SECONDS)
            .apply {
                if (registrarTrafico) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                }
                // Va después del logging para que el Logcat muestre la solicitud y la respuesta simulada
                if (servidorSimulado != null) addInterceptor(servidorSimulado)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(urlBase)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
            .build()
            .create(RecordatoriosApi::class.java)
    }
}
