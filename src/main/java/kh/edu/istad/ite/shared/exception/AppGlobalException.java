package kh.edu.istad.ite.shared.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class AppGlobalException {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ErrorResponse handleValidationEx(MethodArgumentNotValidException e) {
        List<FiledErrorResponse> filedErrorResponseList = new ArrayList<>();
        e.getFieldErrors().forEach(fieldError ->
                filedErrorResponseList.add(
                        new FiledErrorResponse(
                                fieldError.getField(),
                                fieldError.getDefaultMessage()
                        )
                )
        );
        return ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Request data is invalid..!")
                .timestamp(Instant.now())
                .errorDetail(filedErrorResponseList)
                .build();
    }

    @ExceptionHandler(value = ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleServiceEx(ResponseStatusException e) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(e.getStatusCode().toString())
                .code(e.getStatusCode().value())
                .message(e.getReason())
                .timestamp(Instant.now())
                .build();

        return new ResponseEntity<>(errorResponse, e.getStatusCode());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResponse handleUnreadableMessage(HttpMessageNotReadableException e) {
        return ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Request body is invalid or malformed")
                .timestamp(Instant.now())
                .build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException e) {
        List<FiledErrorResponse> filedErrorResponseList = e.getConstraintViolations()
                .stream()
                .map(violation -> new FiledErrorResponse(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Request data is invalid..!")
                .timestamp(Instant.now())
                .errorDetail(filedErrorResponseList)
                .build();
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(DataAccessException.class)
    public ErrorResponse handleDataAccess(DataAccessException e) {
        log.error("Database error", e);
        return ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Database operation failed")
                .timestamp(Instant.now())
                .build();
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Unexpected server error")
                .timestamp(Instant.now())
                .build();
    }

}
