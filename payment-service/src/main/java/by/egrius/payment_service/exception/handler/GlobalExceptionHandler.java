package by.egrius.payment_service.exception.handler;

import by.egrius.payment_service.exception.ResourceNotFoundException;
import by.egrius.payment_service.exception.payment_service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return buildErrorResponse(
                ex,
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed: " + errors,
                null
        );
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        log.warn("Account not found: {}", e.getMessage());
        return buildErrorResponse(e, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return buildErrorResponse(e, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransferNotFound(TransferNotFoundException e) {
        log.warn("Transfer not found: {}", e.getMessage());
        return buildErrorResponse(e, HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        log.warn("Insufficient funds: {}", e.getMessage());
        Map<String, Object> details = Map.of(
                "available", e.getAvailable(),
                "requested", e.getRequested()
        );
        return buildErrorResponse(e, HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", e.getMessage(), details);
    }

    @ExceptionHandler(SameAccountTransferException.class)
    public ResponseEntity<ErrorResponse> handleSameAccountTransfer(SameAccountTransferException e) {
        log.warn("Same account transfer attempted: {}", e.getMessage());
        return buildErrorResponse(e, HttpStatus.BAD_REQUEST, "SAME_ACCOUNT_TRANSFER", e.getMessage());
    }

    @ExceptionHandler(InvalidTransferAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAmount(InvalidTransferAmountException e) {
        log.warn("Invalid transfer amount: {}", e.getMessage());
        return buildErrorResponse(e, HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", e.getMessage());
    }


    @ExceptionHandler(TransferAlreadyProcessedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyProcessed(TransferAlreadyProcessedException e) {
        log.warn("Transfer already processed: {}", e.getMessage());
        return buildErrorResponse(e, HttpStatus.CONFLICT, "ALREADY_PROCESSED", e.getMessage());
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccountAccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return buildErrorResponse(e, HttpStatus.FORBIDDEN, "ACCESS_DENIED", e.getMessage());
    }

    @ExceptionHandler(InvalidLeaderboardParamsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParams(InvalidLeaderboardParamsException e) {
        log.warn("Invalid leaderboard params: {}", e.getMessage());
        return buildErrorResponse(e, HttpStatus.BAD_REQUEST, "INVALID_PARAMS", e.getMessage());
    }

    @ExceptionHandler(TransferProcessingException.class)
    public ResponseEntity<ErrorResponse> handleProcessing(TransferProcessingException e) {
        log.error("Transfer processing failed: {}", e.getMessage(), e);
        return buildErrorResponse(e, HttpStatus.INTERNAL_SERVER_ERROR, "PROCESSING_ERROR", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unexpected error", e);
        return buildErrorResponse(
                e,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later."
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(Throwable ex, HttpStatus status, String code, String message) {
        return buildErrorResponse(ex, status, code, message, null);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(Throwable ex, HttpStatus status, String code,
                                                                                                         String message, Map<String, Object> details) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .code(code)
            .message(message)
            .details(details)
            .build();

        return ResponseEntity.status(status).body(response);
    }
}