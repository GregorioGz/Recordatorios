package com.example.recordatorios.ui.lista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Pide el uid de otra cuenta para la prueba de autorización (solo dev).
 * La operación va directo a Firestore: la interfaz no filtra nada, deciden las reglas.
 */
@Composable
fun DialogoOtraCuenta(
    prueba: PruebaOtraCuentaUiState,
    onUidCambiado: (String) -> Unit,
    onEjecutar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val escribir = prueba.operacion == Simulacion.ESCRIBIR_OTRA_CUENTA
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!prueba.ejecutando) onCancelar() },
        title = { Text(prueba.operacion.etiqueta) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (escribir) "Se intentará crear un documento en users/{uid}/reminders de esa cuenta."
                    else "Se intentará leer users/{uid}/reminders de esa cuenta directamente en Firestore."
                )
                OutlinedTextField(
                    value = prueba.uid,
                    onValueChange = onUidCambiado,
                    label = { Text("uid de la otra cuenta") },
                    singleLine = true,
                    enabled = !prueba.ejecutando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false)
                )
                Text("El uid de cada cuenta se ve (y se puede copiar) en Ajustes.")
            }
        },
        confirmButton = {
            TextButton(onClick = onEjecutar, enabled = !prueba.ejecutando && prueba.uid.isNotBlank()) {
                Text(if (prueba.ejecutando) "Probando…" else if (escribir) "Intentar escribir" else "Intentar leer")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !prueba.ejecutando) { Text("Cancelar") }
        }
    )
}

/** Resultado de una prueba, en un diálogo (queda visible para la captura). */
@Composable
fun DialogoResultadoPrueba(
    resultado: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onCerrar,
        title = { Text("Resultado de la prueba") },
        text = { SelectionContainer { Text(resultado) } },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Aceptar") } }
    )
}
