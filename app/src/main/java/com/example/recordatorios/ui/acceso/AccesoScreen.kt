package com.example.recordatorios.ui.acceso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordatorios.ui.components.CampoDeTexto
import com.example.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun AccesoRoute(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AccesoScreen(
        uiState = uiState,
        onEmailCambiado = viewModel::onEmailCambiado,
        onContrasenaCambiada = viewModel::onContrasenaCambiada,
        onConfirmacionCambiada = viewModel::onConfirmacionCambiada,
        onEnviar = viewModel::onEnviar,
        onAlternarModo = viewModel::onAlternarModo
    )
}

/** Pantalla de acceso (inicio de sesión y registro). Solo dibuja el estado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccesoScreen(
    uiState: AuthUiState,
    onEmailCambiado: (String) -> Unit,
    onContrasenaCambiada: (String) -> Unit,
    onConfirmacionCambiada: (String) -> Unit,
    onEnviar: () -> Unit,
    onAlternarModo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.modo.titulo) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.avisoSesion?.let { AvisoAcceso(texto = it) }

            Text(
                text = "Tus recordatorios se guardan en el dispositivo y se sincronizan con tu cuenta.",
                style = MaterialTheme.typography.bodyMedium
            )
            CampoDeTexto(
                valor = uiState.email,
                onValorCambiado = onEmailCambiado,
                etiqueta = "Email",
                mensajeError = uiState.errorEmail,
                enabled = !uiState.cargando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            CampoDeTexto(
                valor = uiState.contrasena,
                onValorCambiado = onContrasenaCambiada,
                etiqueta = "Contraseña",
                mensajeError = uiState.errorContrasena,
                enabled = !uiState.cargando,
                ayuda = if (uiState.modo == ModoAcceso.REGISTRO) "Mínimo 6 caracteres" else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
            )
            if (uiState.modo == ModoAcceso.REGISTRO) {
                CampoDeTexto(
                    valor = uiState.confirmacion,
                    onValorCambiado = onConfirmacionCambiada,
                    etiqueta = "Repetir contraseña",
                    mensajeError = uiState.errorConfirmacion,
                    enabled = !uiState.cargando,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                )
            }

            val envio = uiState.envio
            if (envio is EnvioAuth.Error) AvisoAcceso(texto = envio.mensaje)

            Button(
                onClick = onEnviar,
                enabled = !uiState.cargando,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.cargando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(uiState.modo.textoBoton)
                }
            }
            TextButton(
                onClick = onAlternarModo,
                enabled = !uiState.cargando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(uiState.modo.textoAlternar)
            }
        }
    }
}

/** Mensaje de error o aviso, anunciado por lectores de pantalla. */
@Composable
private fun AvisoAcceso(texto: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(
            text = "⚠ $texto",
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AccesoErrorPreview() {
    RecordatoriosTheme {
        AccesoScreen(
            uiState = AuthUiState(email = "ana@ejemplo.com", envio = EnvioAuth.Error("El email o la contraseña no son correctos.")),
            onEmailCambiado = {}, onContrasenaCambiada = {}, onConfirmacionCambiada = {}, onEnviar = {}, onAlternarModo = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RegistroPreview() {
    RecordatoriosTheme {
        AccesoScreen(
            uiState = AuthUiState(modo = ModoAcceso.REGISTRO),
            onEmailCambiado = {}, onContrasenaCambiada = {}, onConfirmacionCambiada = {}, onEnviar = {}, onAlternarModo = {}
        )
    }
}
