package edu.sjpiicd.scholarship.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Owns the single {@link ApplicantPriorityQueue} instance and the list of awarded slots.
 *
 * <p>Every operation is synchronised because a web server handles requests on many threads while the
 * heap itself is deliberately not thread-safe. Nothing is persisted: the queue lives in the running
 * Java process and resets when that process restarts.
 */
@Service
public class ScholarshipService {

    /** Applications that have been received but not yet awarded a slot. */
    private final ApplicantPriorityQueue queue = new ApplicantPriorityQueue();

    /** Slots already granted, in the order they were granted. */
    private final List<AwardedView> awarded = new ArrayList<>();

    /** Scholarship slots available for the term. */
    private final int totalSlots;

    /** Submission counter; also supplies the tie-breaking sequence number. */
    private long submissionCounter;

    public ScholarshipService(@Value("${scholarship.slots:5}") int totalSlots) {
        if (totalSlots < 1) {
            throw new IllegalArgumentException("Scholarship slots must be at least 1.");
        }
        this.totalSlots = totalSlots;
    }

    /**
     * Scores an application and enqueues it.
     *
     * <p>The merit score is computed first, then a single {@code insert} places the applicant in the
     * heap. No sorting happens here: the applicant simply rises until its parent outranks it.
     */
    public synchronized ActionResponse submit(ApplicationRequest request) {
        submissionCounter++;
        Applicant applicant = Applicant.of(
                submissionCounter,
                request.name(),
                request.program(),
                request.gwa(),
                request.monthlyIncome(),
                request.unitsEnrolled());

        HeapOperationTrace trace = queue.insert(applicant);
        QueueReport report = buildReport();
        Integer rank = rankOf(report, applicant.sequence());

        String message = "%s entered the queue at rank %d of %d with a merit score of %.2f."
                .formatted(applicant.name(), rank == null ? report.waitingCount() : rank, report.waitingCount(), applicant.meritScore());

        return new ActionResponse(message, ApplicantView.of(applicant, rank, heapIndexOf(report, applicant.sequence())), trace, report);
    }

    /**
     * Grants the next scholarship slot to the applicant at the root of the heap.
     *
     * <p>This is the operation the whole system is built around: a single {@code extractMax} removes
     * the highest merit applicant in O(log n) time without ever sorting the remaining applications.
     *
     * @throws QueueOperationException when no slots remain or the queue is empty
     */
    public synchronized ActionResponse awardNextSlot() {
        if (awarded.size() >= totalSlots) {
            throw new QueueOperationException(
                    "All %d scholarship slots have already been awarded.".formatted(totalSlots));
        }
        if (queue.isEmpty()) {
            throw new QueueOperationException("No applications are waiting in the queue.");
        }

        ApplicantPriorityQueue.ExtractionResult result = queue.extractMax();
        Applicant winner = result.applicant();
        int slotNumber = awarded.size() + 1;
        awarded.add(new AwardedView(slotNumber, ApplicantView.of(winner, slotNumber, null)));

        QueueReport report = buildReport();
        String message = "Slot %d of %d awarded to %s with a merit score of %.2f."
                .formatted(slotNumber, totalSlots, winner.name(), winner.meritScore());

        return new ActionResponse(message, ApplicantView.of(winner, slotNumber, null), result.trace(), report);
    }

    /** Clears the queue, the awarded list, and the submission counter. */
    public synchronized ActionResponse reset() {
        queue.clear();
        awarded.clear();
        submissionCounter = 0;
        return new ActionResponse("Queue cleared. No applications are waiting.", null, null, buildReport());
    }

    /**
     * Loads a fixed set of applications so the heap can be demonstrated without typing.
     *
     * <p>The set is chosen to exercise the structure: one applicant arrives last but outranks every
     * earlier entry and must rise to the root, and two applicants score exactly the same so the
     * submission-order tie-break is visible.
     */
    public synchronized ActionResponse loadSampleApplications() {
        reset();
        List<ApplicationRequest> samples = List.of(
                new ApplicationRequest("Maria Clara Santos", "BS Information Technology", 1.75, 18000.0, 24),
                new ApplicationRequest("Juan Miguel Reyes", "BS Computer Science", 2.40, 32000.0, 21),
                new ApplicationRequest("Andrea Faith Lim", "BS Information Systems", 2.10, 12000.0, 24),
                new ApplicationRequest("Carlos Emmanuel Dizon", "BS Computer Engineering", 2.75, 45000.0, 18),
                new ApplicationRequest("Kyla Marie Ocampo", "BS Information Technology", 2.10, 12000.0, 24),
                new ApplicationRequest("Rafael Antonio Cruz", "BS Computer Science", 3.10, 52000.0, 15),
                new ApplicationRequest("Bea Angeline Navarro", "BS Information Technology", 1.25, 9000.0, 24));
        for (ApplicationRequest sample : samples) {
            submit(sample);
        }
        QueueReport report = buildReport();
        return new ActionResponse(
                "Loaded %d sample applications. %s currently holds the root of the heap."
                        .formatted(samples.size(), report.nextInLine() == null ? "Nobody" : report.nextInLine().name()),
                null,
                null,
                report);
    }

    /** Returns the current queue state without changing anything. */
    public synchronized QueueReport report() {
        return buildReport();
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private QueueReport buildReport() {
        List<Applicant> ranked = queue.rankedSnapshot();
        List<ApplicantView> waiting = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            Applicant applicant = ranked.get(index);
            waiting.add(ApplicantView.of(applicant, index + 1, null));
        }

        List<Applicant> levelOrder = queue.heapArraySnapshot();
        List<ApplicantView> heapArray = new ArrayList<>(levelOrder.size());
        for (int index = 0; index < levelOrder.size(); index++) {
            heapArray.add(ApplicantView.of(levelOrder.get(index), null, index));
        }

        Applicant next = queue.peek();
        return new QueueReport(
                List.copyOf(waiting),
                List.copyOf(heapArray),
                List.copyOf(awarded),
                next == null ? null : ApplicantView.of(next, 1, 0),
                queue.size(),
                submissionCounter,
                totalSlots,
                totalSlots - awarded.size(),
                queue.height(),
                queue.capacity(),
                queue.satisfiesHeapProperty());
    }

    private Integer rankOf(QueueReport report, long sequence) {
        for (ApplicantView view : report.waiting()) {
            if (view.sequence() == sequence) {
                return view.rank();
            }
        }
        return null;
    }

    private Integer heapIndexOf(QueueReport report, long sequence) {
        for (ApplicantView view : report.heapArray()) {
            if (view.sequence() == sequence) {
                return view.heapIndex();
            }
        }
        return null;
    }

    /** Raised when an operation is not valid for the current queue state. */
    public static class QueueOperationException extends RuntimeException {
        public QueueOperationException(String message) {
            super(message);
        }
    }
}
