package edu.sjpiicd.scholarship.application;

/**
 * The envelope returned by every operation that changes the queue.
 *
 * @param message   a short sentence the interface shows as the status line
 * @param subject   the applicant the operation acted on, or {@code null} for resets
 * @param operation the heap trace for the operation, or {@code null} when no heap work was done
 * @param queue     the queue state after the operation
 */
public record ActionResponse(
        String message, ApplicantView subject, HeapOperationTrace operation, QueueReport queue) {
}
