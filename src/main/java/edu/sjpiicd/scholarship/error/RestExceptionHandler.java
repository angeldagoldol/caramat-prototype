package edu.sjpiicd.scholarship.error;

import edu.sjpiicd.scholarship.application.ScholarshipService.QueueOperationException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns rejected requests into a consistent {@link ApiError} body.
 *
 * <p>A rejected request never reaches the heap, so the queue is unchanged whenever one of these
 * handlers runs and the browser can safely keep the values the user typed.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    /** Bean validation failures on the request body, one message per invalid field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<String> messages = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            messages.add(fieldError.getDefaultMessage());
        }
        if (messages.isEmpty()) {
            messages.add("The submitted application is not valid.");
        }
        return build(HttpStatus.BAD_REQUEST, "The submitted application is not valid.", messages);
    }

    /** Malformed JSON, or a number sent where the record expects one and cannot parse it. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return build(
                HttpStatus.BAD_REQUEST,
                "The submitted application could not be read.",
                List.of("Send a JSON body with name, program, gwa, monthlyIncome, and unitsEnrolled."));
    }

    /** Operations that are valid JSON but wrong for the current queue state. */
    @ExceptionHandler(QueueOperationException.class)
    public ResponseEntity<ApiError> handleQueueState(QueueOperationException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), List.of(exception.getMessage()));
    }

    /** Raised by the heap when an extraction is attempted on an empty queue. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleEmptyQueue(NoSuchElementException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), List.of(exception.getMessage()));
    }

    /** Guard rails inside the heap and the scorer, such as a null applicant. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of(exception.getMessage()));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> errors) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, errors));
    }
}
