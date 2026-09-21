package edu.sjpiicd.scholarship.error;

import java.util.List;

/**
 * The JSON body returned for every rejected request.
 *
 * @param status  the HTTP status code
 * @param message a single sentence summarising the failure
 * @param errors  one entry per failed field, or a single entry for non-field failures
 */
public record ApiError(int status, String message, List<String> errors) {

    public ApiError {
        errors = List.copyOf(errors);
    }
}
