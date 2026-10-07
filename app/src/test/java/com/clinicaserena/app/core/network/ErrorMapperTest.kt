package com.clinicaserena.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMapperTest {

    private val mapper = ErrorMapper(NetworkModule.json)

    private fun body(status: Int, code: String, fieldErrors: String = "[]") =
        """{"status":$status,"code":"$code","message":"Mensaje $code","fieldErrors":$fieldErrors}"""

    @Test
    fun `401 AUTHENTICATION_FAILED es credenciales incorrectas`() {
        assertEquals(
            ApiResult.InvalidCredentials("Mensaje AUTHENTICATION_FAILED"),
            mapper.map(401, body(401, "AUTHENTICATION_FAILED")),
        )
    }

    @Test
    fun `401 UNAUTHENTICATED es sesion no autenticada`() {
        assertEquals(
            ApiResult.Unauthenticated("UNAUTHENTICATED", "Mensaje UNAUTHENTICATED"),
            mapper.map(401, body(401, "UNAUTHENTICATED")),
        )
    }

    @Test
    fun `401 sin cuerpo se trata como UNAUTHENTICATED`() {
        assertEquals(ApiResult.Unauthenticated("UNAUTHENTICATED", null), mapper.map(401, null))
    }

    @Test
    fun `403 conserva el code`() {
        assertEquals(
            ApiResult.Forbidden("PRACTITIONER_LINK_REQUIRED", "Mensaje PRACTITIONER_LINK_REQUIRED"),
            mapper.map(403, body(403, "PRACTITIONER_LINK_REQUIRED")),
        )
        assertEquals(
            ApiResult.Forbidden("FORBIDDEN", "Mensaje FORBIDDEN"),
            mapper.map(403, body(403, "FORBIDDEN")),
        )
    }

    @Test
    fun `404 conserva el code`() {
        assertEquals(
            ApiResult.NotFound("APPOINTMENT_NOT_FOUND", "Mensaje APPOINTMENT_NOT_FOUND"),
            mapper.map(404, body(404, "APPOINTMENT_NOT_FOUND")),
        )
    }

    @Test
    fun `409 conserva el code de negocio`() {
        assertEquals(
            ApiResult.Conflict("SLOT_NOT_AVAILABLE", "Mensaje SLOT_NOT_AVAILABLE"),
            mapper.map(409, body(409, "SLOT_NOT_AVAILABLE")),
        )
        assertEquals(
            ApiResult.Conflict("ARRIVAL_NOT_REGISTERED", "Mensaje ARRIVAL_NOT_REGISTERED"),
            mapper.map(409, body(409, "ARRIVAL_NOT_REGISTERED")),
        )
    }

    @Test
    fun `400 VALIDATION_ERROR trae los errores por campo`() {
        val result = mapper.map(
            400,
            body(400, "VALIDATION_ERROR", """[{"field":"reason","message":"Obligatorio"}]"""),
        )
        assertEquals(
            ApiResult.Validation("Mensaje VALIDATION_ERROR", listOf(FieldError("reason", "Obligatorio"))),
            result,
        )
    }

    @Test
    fun `otros 4xx conservan status y code`() {
        assertEquals(
            ApiResult.BadRequest(400, "SLOT_IN_PAST", "Mensaje SLOT_IN_PAST"),
            mapper.map(400, body(400, "SLOT_IN_PAST")),
        )
        assertEquals(ApiResult.BadRequest(429, null, null), mapper.map(429, "demasiadas"))
    }

    @Test
    fun `5xx es error de servidor aunque el cuerpo no sea JSON`() {
        assertEquals(ApiResult.ServerError(500), mapper.map(500, body(500, "INTERNAL_ERROR")))
        assertEquals(ApiResult.ServerError(502), mapper.map(502, "<html>Bad Gateway</html>"))
    }
}
