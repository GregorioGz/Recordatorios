package com.example.recordatorios.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Cuenta activa y botón para cerrar la sesión. */
@Composable
fun SeccionCuenta(
    cuenta: String?,
    uid: String?,
    cerrando: Boolean,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cuenta", style = MaterialTheme.typography.labelLarge)
        Text(cuenta ?: "Sesión activa", style = MaterialTheme.typography.bodyLarge)
        if (uid != null) {
            // Solo en dev: el uid se puede seleccionar y copiar para la prueba de acceso a otra cuenta
            SelectionContainer {
                Text(
                    text = "uid: $uid",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(onClick = onCerrarSesion, enabled = !cerrando) {
            if (cerrando) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            }
            Text("  Cerrar sesión")
        }
        Text(
            text = "Al cerrar la sesión se borran de este dispositivo los recordatorios de la cuenta. " +
                "Los ya sincronizados siguen guardados en la nube.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Confirmación cuando hay cambios que todavía no llegaron a la nube. */
@Composable
fun DialogoCierreSesion(
    pendientes: Int,
    onSincronizarYSalir: () -> Unit,
    onSalirIgual: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cambios = if (pendientes == 1) "1 cambio" else "$pendientes cambios"
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onCancelar,
        title = { Text("Tenés $cambios sin sincronizar") },
        text = {
            Text(
                "Si salís igual, esos cambios se pierden: se borran de este dispositivo y nunca llegaron a la nube."
            )
        },
        confirmButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = onSincronizarYSalir) { Text("Sincronizar y salir") }
                TextButton(onClick = onSalirIgual) {
                    Text("Salir igual", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancelar) { Text("Cancelar") }
            }
        }
    )
}
