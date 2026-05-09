package com.msa.shop_orders.common.handler;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.common.exception.BusinessException;
import org.hibernate.exception.JDBCConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String SERVICE_UNAVAILABLE_MESSAGE = "We're having trouble connecting right now. Please try again in a moment.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        LOGGER.error(
                "Business exception raised: errorCode={}, status={}, message={}",
                exception.getErrorCode(),
                exception.getHttpStatus(),
                exception.getMessage()
        );
        return ResponseEntity.status(exception.getHttpStatus())
                .body(ApiResponse.failure(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("error_code", "VALIDATION_ERROR");
        response.put("message", "Request validation failed");
        response.put("errors", validationErrors);
        LOGGER.error("Validation failed with {} field errors: {}", validationErrors.size(), validationErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(Exception exception) {
        LOGGER.error("Unhandled exception", exception);
        if (isServiceUnavailableException(exception)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure("SERVICE_UNAVAILABLE", SERVICE_UNAVAILABLE_MESSAGE));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_SERVER_ERROR", exception.getMessage()));
    }

    private boolean isServiceUnavailableException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CannotCreateTransactionException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof JDBCConnectionException
                    || current instanceof SQLTransientConnectionException
                    || current instanceof SQLNonTransientConnectionException
                    || current instanceof SQLRecoverableException) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("hikaripool")
                        || normalized.contains("connection is not available")
                        || normalized.contains("connection closed")
                        || normalized.contains("communications link failure")
                        || normalized.contains("could not open jpa entitymanager")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }
}
