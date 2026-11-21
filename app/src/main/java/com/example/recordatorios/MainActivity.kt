package com.example.recordatorios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordatorios.data.preferencias.Tema
import com.example.recordatorios.navigation.AppNavHost
import com.example.recordatorios.ui.TemaViewModel
import com.example.recordatorios.ui.theme.RecordatoriosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val temaViewModel: TemaViewModel = viewModel(factory = TemaViewModel.Factory)
            val tema by temaViewModel.tema.collectAsStateWithLifecycle()

            // La preferencia de tema (DataStore) decide el esquema de colores
            val oscuro = when (tema) {
                Tema.SISTEMA -> isSystemInDarkTheme()
                Tema.CLARO -> false
                Tema.OSCURO -> true
            }
            RecordatoriosTheme(darkTheme = oscuro) {
                // Toda la navegación vive en AppNavHost
                AppNavHost()
            }
        }
    }
}
