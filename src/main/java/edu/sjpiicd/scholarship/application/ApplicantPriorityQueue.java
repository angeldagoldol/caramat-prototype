package edu.sjpiicd.scholarship.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The core data structure of the Online Scholarship Application System: a binary max-heap used as a
 * priority queue of scholarship applicants.
 *
 * <p>This class is written from first principles rather than delegating to
 * {@code java.util.PriorityQueue} so every heap operation is visible and can be explained during
 * checking.
 *
 * <h2>Representation</h2>
 *
 * <p>The heap is a complete binary tree flattened into a plain array. For the element at index
 * {@code i}:
 *
 * <ul>
 *   <li>its parent sits at {@code (i - 1) / 2}</li>
 *   <li>its left child sits at {@code 2 * i + 1}</li>
 *   <li>its right child sits at {@code 2 * i + 2}</li>
 * </ul>
 *
 * <p>No node links are stored. Because the tree is always complete, the array has no gaps and the
 * index arithmetic above is enough to walk the tree in either direction.
 *
 * <h2>Heap property</h2>
 *
 * <p>Every parent has priority greater than or equal to both of its children, so the highest
 * priority applicant is always at index 0 and {@link #peek()} is a constant-time read. Priority is
 * decided by {@link #hasHigherPriority(Applicant, Applicant)}: the larger merit score wins, and an
 * exact tie is settled in favour of the smaller submission sequence. That tie-break makes the
 * ordering total and deterministic, so a tie between two applicants is resolved by whoever applied
 * first rather than by where they happen to sit in the array.
 *
 * <h2>Complexity</h2>
 *
 * <ul>
 *   <li>{@link #insert(Applicant)} - O(log n); the new element rises at most the height of the tree</li>
 *   <li>{@link #extractMax()} - O(log n); the replacement root sinks at most the height of the tree</li>
 *   <li>{@link #peek()} and {@link #size()} - O(1)</li>
 *   <li>{@link #rankedSnapshot()} - O(n log n); a heap sort over a copy that leaves this heap untouched</li>
 * </ul>
 *
 * <p>This class is not thread-safe. {@link ScholarshipService} owns the only instance and
 * synchronises access to it.
 */
public class ApplicantPriorityQueue {

    /** Capacity used when the queue is created without an explicit one. */
    static final int DEFAULT_CAPACITY = 16;

    /** Backing array. Positions 0 through {@code size - 1} hold the complete binary tree. */
    private Applicant[] heap;

    /** Number of applicants currently stored. Also the index of the next free slot. */
    private int size;

    /** Comparisons performed by the operation currently running; reset at the start of each call. */
    private int comparisons;

    public ApplicantPriorityQueue() {
        this(DEFAULT_CAPACITY);
    }

    public ApplicantPriorityQueue(int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException("Initial capacity must be at least 1.");
        }
        this.heap = new Applicant[initialCapacity];
        this.size = 0;
    }

    // ---------------------------------------------------------------------
    // Index arithmetic
    // ---------------------------------------------------------------------

    private static int parentOf(int index) {
        return (index - 1) / 2;
    }

    private static int leftChildOf(int index) {
        return 2 * index + 1;
    }

    private static int rightChildOf(int index) {
        return 2 * index + 2;
    }

    // ---------------------------------------------------------------------
    // Ordering
    // ---------------------------------------------------------------------

    /**
     * Returns {@code true} when {@code candidate} outranks {@code reference}.
     *
     * <p>The higher merit score wins. When the scores are exactly equal the applicant who submitted
     * first wins, which keeps the queue stable for applicants of identical standing.
     */
    static boolean hasHigherPriority(Applicant candidate, Applicant reference) {
        int byScore = Double.compare(candidate.meritScore(), reference.meritScore());
        if (byScore != 0) {
            return byScore > 0;
        }
        return candidate.sequence() < reference.sequence();
    }

    /** Wraps {@link #hasHigherPriority} so the running operation can count the comparison. */
    private boolean outranks(int candidateIndex, int referenceIndex) {
        comparisons++;
        return hasHigherPriority(heap[candidateIndex], heap[referenceIndex]);
    }

    // ---------------------------------------------------------------------
    // Core operations
    // ---------------------------------------------------------------------

    /**
     * Inserts an applicant and restores the heap property by sifting the new element up.
     *
     * <p>The applicant is appended at the first free array slot, which keeps the tree complete, then
     * repeatedly swapped with its parent for as long as it outranks that parent. The loop runs at
     * most once per level, giving O(log n) time.
     *
     * @param applicant the applicant to enqueue, never {@code null}
     * @return the comparisons and swaps this insertion needed
     */
    public HeapOperationTrace insert(Applicant applicant) {
        if (applicant == null) {
            throw new IllegalArgumentException("Applicant must not be null.");
        }
        comparisons = 0;
        List<String> steps = new ArrayList<>();

        growIfFull();
        int current = size;
        heap[current] = applicant;
        size++;

        // Sift up: rise while this element outranks its parent.
        while (current > 0 && outranks(current, parentOf(current))) {
            int parent = parentOf(current);
            steps.add(describeSwap(current, parent, "rises above"));
            swap(current, parent);
            current = parent;
        }

        return new HeapOperationTrace("insert", comparisons, steps.size(), steps);
    }

    /**
     * Removes and returns the highest priority applicant, then restores the heap property by sifting
     * the replacement root down.
     *
     * <p>The root is taken, the last element is moved into index 0 so the tree stays complete, and
     * that element then sinks: at each level it is compared with both children and swapped with the
     * stronger of the two whenever a child outranks it. The loop runs at most once per level, giving
     * O(log n) time.
     *
     * @return the removed applicant together with the trace of the operation
     * @throws NoSuchElementException when the queue is empty
     */
    public ExtractionResult extractMax() {
        if (isEmpty()) {
            throw new NoSuchElementException("No applications are waiting in the queue.");
        }
        comparisons = 0;
        List<String> steps = new ArrayList<>();

        Applicant highest = heap[0];
        size--;
        heap[0] = heap[size];
        heap[size] = null;

        // Sift down: sink while a child outranks this element.
        int current = 0;
        while (true) {
            int left = leftChildOf(current);
            int right = rightChildOf(current);
            int strongest = current;

            if (left < size && outranks(left, strongest)) {
                strongest = left;
            }
            if (right < size && outranks(right, strongest)) {
                strongest = right;
            }
            if (strongest == current) {
                break;
            }
            steps.add(describeSwap(strongest, current, "sinks below"));
            swap(current, strongest);
            current = strongest;
        }

        return new ExtractionResult(
                highest, new HeapOperationTrace("extractMax", comparisons, steps.size(), steps));
    }

    /**
     * Returns the highest priority applicant without removing it.
     *
     * @return the root of the heap, or {@code null} when the queue is empty
     */
    public Applicant peek() {
        return isEmpty() ? null : heap[0];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Capacity of the backing array; exposed so the interface can show the array doubling. */
    public int capacity() {
        return heap.length;
    }

    /** Empties the queue and releases every stored reference. */
    public void clear() {
        Arrays.fill(heap, 0, size, null);
        size = 0;
    }

    // ---------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------

    /**
     * Returns the backing array in level order, from the root outward.
     *
     * <p>This is the raw heap layout rather than a sorted list. The interface renders it so the
     * complete binary tree behind the queue can be inspected directly.
     */
    public List<Applicant> heapArraySnapshot() {
        List<Applicant> snapshot = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            snapshot.add(heap[index]);
        }
        return List.copyOf(snapshot);
    }

    /**
     * Returns every waiting applicant ordered from highest to lowest priority.
     *
     * <p>A heap is only partially ordered, so producing a full ranking takes real work: this method
     * copies the backing array and runs a heap sort over the copy by repeatedly extracting the
     * maximum. The live queue is never modified, so calling this to refresh the screen has no effect
     * on who gets awarded next.
     */
    public List<Applicant> rankedSnapshot() {
        ApplicantPriorityQueue copy = new ApplicantPriorityQueue(Math.max(size, 1));
        System.arraycopy(heap, 0, copy.heap, 0, size);
        copy.size = size;

        List<Applicant> ranked = new ArrayList<>(size);
        while (!copy.isEmpty()) {
            ranked.add(copy.extractMax().applicant());
        }
        return List.copyOf(ranked);
    }

    /**
     * Verifies the heap property over the whole array.
     *
     * <p>Used by the tests and by the {@code /api/queue} response as a self-check that the structure
     * stayed valid after every insertion and extraction.
     */
    public boolean satisfiesHeapProperty() {
        for (int index = 1; index < size; index++) {
            if (hasHigherPriority(heap[index], heap[parentOf(index)])) {
                return false;
            }
        }
        return true;
    }

    /** Height of the complete binary tree, used to show the log2(n) bound on screen. */
    public int height() {
        if (size == 0) {
            return 0;
        }
        return (int) (Math.log(size) / Math.log(2)) + 1;
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /** Doubles the backing array when the tree has filled it. */
    private void growIfFull() {
        if (size == heap.length) {
            heap = Arrays.copyOf(heap, heap.length * 2);
        }
    }

    private void swap(int first, int second) {
        Applicant held = heap[first];
        heap[first] = heap[second];
        heap[second] = held;
    }

    private String describeSwap(int moving, int other, String verb) {
        return "%s (%.2f) %s %s (%.2f): index %d <-> index %d"
                .formatted(
                        heap[moving].name(),
                        heap[moving].meritScore(),
                        verb,
                        heap[other].name(),
                        heap[other].meritScore(),
                        moving,
                        other);
    }

    /**
     * The applicant removed by {@link #extractMax()} together with the trace of that removal.
     *
     * @param applicant the applicant that held the highest priority
     * @param trace     the comparisons and swaps performed while sifting down
     */
    public record ExtractionResult(Applicant applicant, HeapOperationTrace trace) {
    }
}
