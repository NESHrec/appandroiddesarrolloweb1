package com.clinicaserena.app.domain.model

import java.time.OffsetDateTime

/**
 * Motivos por los que Spring no permite documentar una cita (`attentionBlockers`). Sus nombres no
 * coinciden con los códigos 409 del POST: STATUS_NOT_DOCUMENTABLE ↔ APPOINTMENT_NOT_DOCUMENTABLE y
 * NOT_STARTED ↔ APPOINTMENT_NOT_STARTED.
 */
enum class AttentionBlocker {
    ATTENTION_ALREADY_RECORDED, STATUS_NOT_DOCUMENTABLE, NOT_STARTED, ARRIVAL_NOT_REGISTERED, DESCONOCIDO;

    companion object {
        fun from(value: String): AttentionBlocker = entries.firstOrNull { it.name == value } ?: DESCONOCIDO
    }
}

/** Cita vista por su médico (`MedicalAppointmentResponse`). */
data class DoctorAppointment(
    val id: String,
    val patientId: String,
    val patientName: String?,
    val specialtyName: String?,
    val scheduledAt: OffsetDateTime,
    val status: AppointmentStatus,
    val statusRaw: String,
    val notes: String?,
    val arrivalAt: OffsetDateTime?,
    val attentionRecorded: Boolean,
    val canRecordAttention: Boolean,
    /** En el orden en que los envía Spring (el mismo con el que valida al guardar). */
    val blockers: List<AttentionBlocker>,
    val blockersRaw: List<String>,
)

data class DoctorAppointmentDetail(
    val appointment: DoctorAppointment,
    val attention: Attention?,
)
