package com.example.recordatorios

import android.app.Application

/** Punto de entrada del proceso: inicializa el contenedor de dependencias una sola vez. */
class RecordatoriosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.inicializar(this)
    }
}
