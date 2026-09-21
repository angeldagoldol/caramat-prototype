package edu.sjpiicd.scholarship.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The payload accepted by {@code POST /api/applications}.
 *
 * <p>Java owns the authoritative validation. The browser performs the same required-field checks
 * first, but a request that bypasses the browser is still rejected here and the queue is left
 * unchanged.
 */
public record ApplicationRequest(
        @NotBlank(message = "Applicant name is required.")
        @Size(max = 80, message = "Applicant name must be 80 characters or fewer.")
        String name,

        @NotBlank(message = "Degree program is required.")
        @Size(max = 80, message = "Degree program must be 80 characters or fewer.")
        String program,

        @NotNull(message = "General weighted average is required.")
        @DecimalMin(value = "1.00", message = "General weighted average must be 1.00 or higher.")
        @DecimalMax(value = "5.00", message = "General weighted average must be 5.00 or lower.")
        Double gwa,

        @NotNull(message = "Monthly household income is required.")
        @DecimalMin(value = "0", message = "Monthly household income must be 0 or greater.")
        @DecimalMax(value = "1000000", message = "Monthly household income must be 1,000,000 or lower.")
        Double monthlyIncome,

        @NotNull(message = "Units enrolled is required.")
        @Min(value = 1, message = "Units enrolled must be at least 1.")
        @Max(value = 36, message = "Units enrolled must be 36 or fewer.")
        Integer unitsEnrolled) {

    /** Trims the free-text fields so stored names never carry stray whitespace. */
    public ApplicationRequest {
        name = name == null ? null : name.trim();
        program = program == null ? null : program.trim();
    }
}
