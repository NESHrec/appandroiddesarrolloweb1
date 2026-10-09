package com.clinicaserena.app.domain.model

import java.time.OffsetDateTime

/*
 * Datos clínicos del paciente, solo para mostrar. Viven en memoria (ViewModel de la sesión) y nunca se
 * guardan en disco. No incluyen IDs de cuentas del personal: al paciente se le muestra el nombre.
 */

data class PrescriptionItem(
    val order: Int,
    val medicine: String,
    val dose: String,
    val frequency: String,
    val duration: String,
    val instructions: String?,
)

data class Prescription(
    val id: String,
    val appointmentScheduledAt: OffsetDateTime?,
    val issuedAt: OffsetDateTime,
    val practitionerName: String?,
    val items: List<PrescriptionItem>,
)

/** Seguimiento derivado de una atención registrada (`/pacientes/me/chequeos`). */
data class Checkup(
    val id: String,
    val appointmentScheduledAt: OffsetDateTime?,
    val recordedAt: OffsetDateTime,
    val practitionerName: String?,
    val reason: String,
    val findings: String?,
    val diagnosis: String,
    val treatmentPlan: String?,
    val prescription: List<PrescriptionItem>,
)

data class ClinicalProfile(
    val allergies: String?,
    val relevantConditions: String?,
    val currentMedications: String?,
    val dentalHistory: String?,
    val authorName: String?,
    val recordedAt: OffsetDateTime,
)

data class Addendum(
    val text: String,
    val reason: String,
    val authorName: String?,
    val recordedAt: OffsetDateTime,
)

data class Attention(
    val id: String,
    val appointmentScheduledAt: OffsetDateTime?,
    val practitionerName: String?,
    val authorName: String?,
    val reason: String,
    val findings: String?,
    val diagnosis: String,
    val treatmentPlan: String?,
    val recordedAt: OffsetDateTime,
    val prescription: List<PrescriptionItem>,
    val addenda: List<Addendum>,
)

/** Expediente propio. [recordId] es `null` hasta la primera atención. */
data class ClinicalRecord(
    val recordId: String?,
    val profile: ClinicalProfile?,
    val profileHistory: List<ClinicalProfile>,
    val attentions: List<Attention>,
)
