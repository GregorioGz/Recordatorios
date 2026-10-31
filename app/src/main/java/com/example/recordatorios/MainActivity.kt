package com.example.recordatorios

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.ResultadoValidacion
import com.example.recordatorios.model.Validador
import com.example.recordatorios.ui.PantallaRecordatorios
import com.example.recordatorios.ui.theme.RecordatoriosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        probarValidaciones()
        enableEdgeToEdge()
        setContent {
            RecordatoriosTheme {
                PantallaRecordatorios()
            }
        }
    }

    /** Demuestra en Logcat (filtro: "Validacion") que las reglas evitan estados inválidos. */
    private fun probarValidaciones() {
        val categoria = DatosSimulados.categorias.first()
        val casos = listOf(
            Validador.crearRecordatorio(100, "", categoria, null),
            Validador.crearRecordatorio(101, "Llamar al banco", categoria, null, avisoMinutosAntes = -5),
            Validador.crearRecordatorio(102, "Pagar servicios", categoria, Prioridad.Media, avisoMinutosAntes = 30)
        )
        casos.forEach { resultado ->
            when (resultado) {
                is ResultadoValidacion.Valido ->
                    Log.d("Validacion", "OK: ${resultado.recordatorio.titulo}")
                is ResultadoValidacion.Invalido ->
                    Log.w("Validacion", "Rechazado: ${resultado.errores.joinToString()}")
            }
        }
    }
}