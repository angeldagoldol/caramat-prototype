package edu.sjpiicd.scholarship.application;

import java.util.List;

/**
 * A complete picture of the queue after an operation, returned by every endpoint.
 *
 * @param waiting        applicants still in the queue, ordered from highest to lowest priority
 * @param heapArray      the same applicants in raw level order, as they sit in the backing array
 * @param awarded        slots already granted, in the order they were granted
 * @param nextInLine     the root of the heap, or {@code null} when nothing is waiting
 * @param waitingCount   number of applicants still in the queue
 * @param totalSubmitted number of applications received since the last reset
 * @param totalSlots     scholarship slots configured for the term
 * @param slotsRemaining slots still available
 * @param heapHeight     height of the complete binary tree, which bounds the cost per operation
 * @param heapCapacity   length of the backing array, which doubles as the queue grows
 * @param heapValid      result of re-checking the heap property across the whole array
 */
public record QueueReport(
        List<ApplicantView> waiting,
        List<ApplicantView> heapArray,
        List<AwardedView> awarded,
        ApplicantView nextInLine,
        int waitingCount,
        long totalSubmitted,
        int totalSlots,
        int slotsRemaining,
        int heapHeight,
        int heapCapacity,
        boolean heapValid) {
}
