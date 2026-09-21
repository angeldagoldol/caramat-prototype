package edu.sjpiicd.scholarship.application;

import java.util.List;

/**
 * A record of the work one heap operation performed.
 *
 * <p>The prototype reports this back to the browser so the number of comparisons and swaps is
 * visible on screen. Watching the counts stay near log2(n) while the queue grows is the clearest
 * demonstration that the structure really is a heap and not a sorted list.
 *
 * @param operation   the operation name, {@code insert} or {@code extractMax}
 * @param comparisons how many applicant-to-applicant priority comparisons were made
 * @param swaps       how many array positions were exchanged while restoring the heap property
 * @param steps       a readable trace of each swap, ordered from first to last
 */
public record HeapOperationTrace(String operation, int comparisons, int swaps, List<String> steps) {

    public HeapOperationTrace {
        steps = List.copyOf(steps);
    }
}
