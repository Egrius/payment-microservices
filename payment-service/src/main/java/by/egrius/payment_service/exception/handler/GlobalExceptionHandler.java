package by.egrius.payment_service.exception.handler;

import by.egrius.payment_service.dto.response.ErrorResponse;
import by.egrius.payment_service.exception.DeterministicException;
import by.egrius.payment_service.exception.ErrorCode;
import by.egrius.payment_service.exception.ResourceNotFoundException;
import by.egrius.payment_service.exception.payment_service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());

        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed: " + errors,
                request,
                null
        );
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleAuthServerError(HttpClientErrorException e, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.valueOf(e.getStatusCode().value()),
                "AUTH_SERVER_ERROR",
                e.getResponseBodyAsString(),
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,  HttpServletRequest request) {
        log.warn("Type mismatch: {}", e.getMessage());

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Invalid value for: " + e.getName(),
                request,
                null
        );
    }

    @ExceptionHandler(DeterministicException.class)
    public ResponseEntity<ErrorResponse> handleDeterministic(DeterministicException e,  HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.valueOf(e.getErrorCode().getHttpStatus()),
                e.getErrorCode().getCode(),
                e.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(IdempotentReplayException.class)
    public ResponseEntity<ErrorResponse> handleReplay(IdempotentReplayException e,  HttpServletRequest request) {

        ErrorResponse body = e.getBody();

        return buildErrorResponse(
                HttpStatus.valueOf(e.getHttpStatus()),
                body.getCode(),
                body.getMessage(),
                request,
                body.getDetails()
        );
    }

    @ExceptionHandler(InvalidSortException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortException(InvalidSortException e, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_SORT",
                e.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e,  HttpServletRequest request) {
        if(e.getHeaderName().equals("Idempotency-Key")) {
            return buildErrorResponse(
                    HttpStatus.BAD_REQUEST,
                    "MISSING_IDEMPOTENCY_KEY",
                    e.getMessage(),
                    request,
                    null);
        }
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_HEADER",
                e.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e,  HttpServletRequest request) {
        log.warn("Account not found: {}", e.getMessage());

        return buildErrorResponse(
                HttpStatus.valueOf(ErrorCode.ACCOUNT_NOT_FOUND.getHttpStatus()),
                ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                e.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e,  HttpServletRequest request) {
        log.warn("Resource not found: {}", e.getMessage());
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                e.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransferNotFound(TransferNotFoundException e,  HttpServletRequest request) {
        log.warn("Transfer not found: {}", e.getMessage());
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "TRANSFER_NOT_FOUND",
                e.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e,  HttpServletRequest request) {
        log.warn("Insufficient funds: {}", e.getMessage());
        Map<String, Object> details = Map.of(
                "available", e.getAvailable(),
                "requested", e.getRequested()
        );
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INSUFFICIENT_FUNDS",
                e.getMessage(),
                request,
                details);
    }

    @ExceptionHandler(InvalidTransferAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAmount(InvalidTransferAmountException e,  HttpServletRequest request) {
        log.warn("Invalid transfer amount: {}", e.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_AMOUNT",
                e.getMessage(),
                request,
                null);
    }


    @ExceptionHandler(TransferNotPendingException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyProcessed(TransferNotPendingException e,  HttpServletRequest request) {
        log.warn("Transfer already processed: {}", e.getMessage());
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "ALREADY_PROCESSED",
                e.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(InvalidLeaderboardParamsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParams(InvalidLeaderboardParamsException e,  HttpServletRequest request) {
        log.warn("Invalid leaderboard params: {}", e.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMS",
                e.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(TransferProcessingException.class)
    public ResponseEntity<ErrorResponse> handleProcessing(TransferProcessingException e,  HttpServletRequest request) {
        log.error("Transfer processing failed: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PROCESSING_ERROR",
                e.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e,  HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.valueOf(e.getStatusCode().value()),
                e.getStatusCode().toString(),
                e.getReason(),
                request,
                null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e,  HttpServletRequest request) {
        log.error("Unexpected error", e);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                request,
                null
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status,
                                                             String code,
                                                             String message,
                                                             HttpServletRequest request,
                                                             Object details) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(
                        LocalDateTime.now(), status.value(),
                        code, message,
                        request.getRequestURI(), details)
        );
    }
}