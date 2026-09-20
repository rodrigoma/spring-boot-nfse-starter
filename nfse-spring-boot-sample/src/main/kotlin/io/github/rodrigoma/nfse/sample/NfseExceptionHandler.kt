package io.github.rodrigoma.nfse.sample

import io.github.rodrigoma.nfse.exception.NfseError
import io.github.rodrigoma.nfse.exception.NfseException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class NfseExceptionHandler {
    @ExceptionHandler(NfseException.Rejected::class)
    fun handleRejected(ex: NfseException.Rejected): ResponseEntity<Map<String, List<NfseError>>> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(mapOf("errors" to ex.errors))

    @ExceptionHandler(NfseException.Validation::class)
    fun handleValidation(ex: NfseException.Validation): ResponseEntity<Map<String, List<NfseError>>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("errors" to ex.errors))

    @ExceptionHandler(NfseException.NotFound::class)
    fun handleNotFound(ex: NfseException.NotFound): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to ex.message.orEmpty()))

    @ExceptionHandler(NfseException.Unauthorized::class)
    fun handleUnauthorized(ex: NfseException.Unauthorized): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(ex.httpStatus).body(mapOf("error" to ex.message.orEmpty()))

    @ExceptionHandler(NfseException.Unavailable::class)
    fun handleUnavailable(ex: NfseException.Unavailable): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(mapOf("error" to ex.message.orEmpty()))
}
