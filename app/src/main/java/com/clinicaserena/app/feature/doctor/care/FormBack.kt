package com.clinicaserena.app.feature.doctor.care

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.clinicaserena.app.R

/** Resultado de pulsar Atrás en un formulario inmutable. */
enum class FormBack {
    /** El formulario lo resolvió (volvió de la confirmación o abrió el diálogo de descartar). */
    STAY,

    /** Salir sin cambios. */
    EXIT,

    /** Salir tras un registro exitoso (la pantalla anterior se recarga). */
    FINISHED,
}

/**
 * Regla común de Atrás para los 4 formularios inmutables (atención, perfil clínico, adenda y
 * observación): desde la confirmación vuelve al formulario; con cambios pide confirmar el descarte;
 * sin cambios sale. El borrador solo vive en el ViewModel: descartarlo no toca el disco.
 */
fun formBack(step: FormStep, hasChanges: Boolean): FormBack = when (step) {
    FormStep.CONFIRM -> FormBack.STAY
    FormStep.DONE -> FormBack.FINISHED
    FormStep.FORM -> if (hasChanges) FormBack.STAY else FormBack.EXIT
}

/**
 * Conecta la flecha de la barra, el botón Atrás y el gesto a la misma acción. Devuelve la lambda para
 * la flecha.
 */
@Composable
fun rememberFormBack(onBack: () -> FormBack, onExit: () -> Unit, onFinished: () -> Unit): () -> Unit {
    val back: () -> Unit = {
        when (onBack()) {
            FormBack.EXIT -> onExit()
            FormBack.FINISHED -> onFinished()
            FormBack.STAY -> Unit
        }
    }
    BackHandler(onBack = back)
    return back
}

@Composable
fun DiscardDraftDialog(visible: Boolean, onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.borrador_descartar_titulo)) },
        text = { Text(stringResource(R.string.borrador_descartar_mensaje)) },
        confirmButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("discard_confirm")) {
                Text(stringResource(R.string.borrador_descartar))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing, modifier = Modifier.testTag("discard_keep")) {
                Text(stringResource(R.string.borrador_seguir_editando))
            }
        },
        modifier = Modifier.testTag("discard_dialog"),
    )
}
