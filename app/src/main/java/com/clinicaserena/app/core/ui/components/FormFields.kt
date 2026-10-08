package com.clinicaserena.app.core.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.fillMaxWidth
import com.clinicaserena.app.R

/**
 * Campo de texto con etiqueta visible y error anunciado por accesibilidad. [contentType] lo marca
 * para el autocompletado del sistema (gestores de contraseñas), por ejemplo correo o usuario.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    testTag: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    contentType: ContentType? = null,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics {
                if (contentType != null) this.contentType = contentType
                if (error != null) error(error)
            },
    )
}

/**
 * Contraseña: oculta por defecto y sin autocorrección ni sugerencias del teclado
 * (`KeyboardType.Password`). Sí participa en el autocompletado del sistema para que funcionen los
 * gestores de contraseñas: [contentType] es `Password` al entrar y `NewPassword` al registrarse.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    error: String?,
    testTag: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    enabled: Boolean = true,
    contentType: ContentType = ContentType.Password,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        trailingIcon = {
            IconButton(onClick = onToggleVisible, modifier = Modifier.testTag("${testTag}_toggle")) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(
                        if (visible) R.string.campo_ocultar_contrasena else R.string.campo_mostrar_contrasena,
                    ),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics {
                this.contentType = contentType
                if (error != null) error(error)
            },
    )
}
