package com.example.recordatorios.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Campo de texto CONTROLADO: no guarda estado propio. Recibe el valor y el
 * error desde el UiState y avisa cada cambio con onValorCambiado.
 * El error se comunica con isError (color), con texto visible y con la
 * propiedad semántica error() para lectores de pantalla.
 */
@Composable
fun CampoDeTexto(
    valor: String,
    onValorCambiado: (String) -> Unit,
    etiqueta: String,
    mensajeError: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxCaracteres: Int? = null,
    ayuda: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorCambiado,
        label = { Text(etiqueta) },
        isError = mensajeError != null,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = if (mensajeError != null) "⚠ $mensajeError" else ayuda.orEmpty(),
                    modifier = Modifier.weight(1f)
                )
                if (maxCaracteres != null) Text("${valor.length}/$maxCaracteres")
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (mensajeError != null) error(mensajeError) }
    )
}

/**
 * Selector CONTROLADO de una opción entre varias (chips de Material 3).
 * Es genérico: sirve para categorías, prioridades o cualquier otra lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectorDeOpciones(
    titulo: String,
    opciones: List<T>,
    seleccionada: T?,
    etiquetaDe: (T) -> String,
    onSeleccion: (T) -> Unit,
    mensajeError: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (mensajeError != null) error(mensajeError) }
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelLarge,
            color = if (mensajeError != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            opciones.forEach { opcion ->
                FilterChip(
                    selected = opcion == seleccionada,
                    onClick = { onSeleccion(opcion) },
                    label = { Text(etiquetaDe(opcion)) },
                    enabled = enabled
                )
            }
        }
        if (mensajeError != null) {
            Text(
                text = "⚠ $mensajeError",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
