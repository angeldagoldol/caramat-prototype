package edu.sjpiicd.scholarship.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicantPriorityQueueTest {

    private final ApplicantPriorityQueue queue = new ApplicantPriorityQueue();

    /** Builds an applicant whose merit score is controlled directly, bypassing the scorer. */
    private static Applicant withScore(long sequence, String name, double score) {
        return new Applicant(
                sequence,
                "SCH-%04d".formatted(sequence),
                name,
                "BS Information Technology",
                2.0,
                20_000,
                24,
                new MeritBreakdown(score, 0.0, 0.0, score));
    }

    @Test
    @DisplayName("a new queue is empty and peeking returns nothing")
    void startsEmpty() {
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isZero();
        assertThat(queue.peek()).isNull();
        assertThat(queue.height()).isZero();
        assertThat(queue.satisfiesHeapProperty()).isTrue();
    }

    @Test
    @DisplayName("extracting from an empty queue is rejected")
    void extractOnEmptyQueueThrows() {
        assertThatThrownBy(queue::extractMax)
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("No applications");
    }

    @Test
    @DisplayName("a null applicant is rejected")
    void nullInsertRejected() {
        assertThatThrownBy(() -> queue.insert(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the highest merit score sits at the root no matter what order it arrives in")
    void highestScoreReachesRoot() {
        queue.insert(withScore(1, "Low", 40.0));
        queue.insert(withScore(2, "Middle", 60.0));
        queue.insert(withScore(3, "Highest", 92.5));
        queue.insert(withScore(4, "Also low", 12.0));

        assertThat(queue.peek().name()).isEqualTo("Highest");
        assertThat(queue.size()).isEqualTo(4);
        assertThat(queue.satisfiesHeapProperty()).isTrue();
    }

    @Test
    @DisplayName("a last-arriving top applicant rises to the root and the swaps are traced")
    void insertSiftsUpAndTraces() {
        queue.insert(withScore(1, "A", 50.0));
        queue.insert(withScore(2, "B", 45.0));
        queue.insert(withScore(3, "C", 40.0));

        HeapOperationTrace trace = queue.insert(withScore(4, "Champion", 99.0));

        assertThat(queue.peek().name()).isEqualTo("Champion");
        assertThat(trace.operation()).isEqualTo("insert");
        assertThat(trace.swaps()).isEqualTo(2);
        assertThat(trace.steps()).hasSize(2);
        assertThat(trace.steps().get(0)).contains("Champion").contains("rises above");
        assertThat(trace.comparisons()).isGreaterThanOrEqualTo(trace.swaps());
    }

    @Test
    @DisplayName("an arriving lowest applicant needs no swap")
    void insertWithoutSwap() {
        queue.insert(withScore(1, "Top", 90.0));
        HeapOperationTrace trace = queue.insert(withScore(2, "Bottom", 10.0));

        assertThat(trace.swaps()).isZero();
        assertThat(trace.steps()).isEmpty();
        assertThat(queue.peek().name()).isEqualTo("Top");
    }

    @Test
    @DisplayName("repeated extraction returns applicants in descending merit order")
    void extractionIsDescending() {
        double[] scores = {55.5, 91.0, 12.25, 78.0, 33.75, 99.99, 64.5};
        for (int index = 0; index < scores.length; index++) {
            queue.insert(withScore(index + 1, "Applicant " + index, scores[index]));
        }

        List<Double> extracted = new ArrayList<>();
        while (!queue.isEmpty()) {
            extracted.add(queue.extractMax().applicant().meritScore());
            assertThat(queue.satisfiesHeapProperty()).isTrue();
        }

        assertThat(extracted)
                .containsExactly(99.99, 91.0, 78.0, 64.5, 55.5, 33.75, 12.25);
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("extraction records the sift-down trace")
    void extractionTracesSiftDown() {
        for (int index = 0; index < 7; index++) {
            queue.insert(withScore(index + 1, "Applicant " + index, 10.0 * (index + 1)));
        }

        ApplicantPriorityQueue.ExtractionResult result = queue.extractMax();

        assertThat(result.applicant().meritScore()).isEqualTo(70.0);
        assertThat(result.trace().operation()).isEqualTo("extractMax");
        assertThat(result.trace().swaps()).isGreaterThan(0);
        assertThat(result.trace().steps()).allSatisfy(step -> assertThat(step).contains("sinks below"));
    }

    @Test
    @DisplayName("equal merit scores are released in submission order")
    void tiesBreakBySubmissionOrder() {
        queue.insert(withScore(3, "Applied third", 80.0));
        queue.insert(withScore(1, "Applied first", 80.0));
        queue.insert(withScore(2, "Applied second", 80.0));

        assertThat(queue.extractMax().applicant().name()).isEqualTo("Applied first");
        assertThat(queue.extractMax().applicant().name()).isEqualTo("Applied second");
        assertThat(queue.extractMax().applicant().name()).isEqualTo("Applied third");
    }

    @Test
    @DisplayName("hasHigherPriority prefers the larger score, then the earlier sequence")
    void priorityRuleIsTotalAndDeterministic() {
        Applicant strong = withScore(9, "Strong", 90.0);
        Applicant weak = withScore(1, "Weak", 50.0);
        Applicant earlyTie = withScore(2, "Early", 70.0);
        Applicant lateTie = withScore(8, "Late", 70.0);

        assertThat(ApplicantPriorityQueue.hasHigherPriority(strong, weak)).isTrue();
        assertThat(ApplicantPriorityQueue.hasHigherPriority(weak, strong)).isFalse();
        assertThat(ApplicantPriorityQueue.hasHigherPriority(earlyTie, lateTie)).isTrue();
        assertThat(ApplicantPriorityQueue.hasHigherPriority(lateTie, earlyTie)).isFalse();
    }

    @Test
    @DisplayName("the backing array doubles instead of overflowing")
    void arrayGrows() {
        ApplicantPriorityQueue small = new ApplicantPriorityQueue(2);
        assertThat(small.capacity()).isEqualTo(2);

        for (int index = 0; index < 9; index++) {
            small.insert(withScore(index + 1, "Applicant " + index, index));
        }

        assertThat(small.size()).isEqualTo(9);
        assertThat(small.capacity()).isGreaterThanOrEqualTo(9);
        assertThat(small.satisfiesHeapProperty()).isTrue();
    }

    @Test
    @DisplayName("the heap array snapshot is level order, not sorted order")
    void snapshotIsLevelOrder() {
        queue.insert(withScore(1, "A", 10.0));
        queue.insert(withScore(2, "B", 20.0));
        queue.insert(withScore(3, "C", 30.0));

        List<Applicant> levelOrder = queue.heapArraySnapshot();

        assertThat(levelOrder).hasSize(3);
        assertThat(levelOrder.get(0).meritScore()).isEqualTo(30.0);
        assertThat(levelOrder).extracting(Applicant::name).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    @DisplayName("the ranked snapshot is fully ordered and leaves the live queue untouched")
    void rankedSnapshotDoesNotMutate() {
        for (int index = 0; index < 10; index++) {
            queue.insert(withScore(index + 1, "Applicant " + index, (index * 37) % 100));
        }
        int sizeBefore = queue.size();
        Applicant rootBefore = queue.peek();

        List<Applicant> ranked = queue.rankedSnapshot();

        assertThat(ranked).hasSize(sizeBefore);
        assertThat(queue.size()).isEqualTo(sizeBefore);
        assertThat(queue.peek()).isEqualTo(rootBefore);
        assertThat(queue.satisfiesHeapProperty()).isTrue();
        for (int index = 1; index < ranked.size(); index++) {
            assertThat(ranked.get(index - 1).meritScore())
                    .isGreaterThanOrEqualTo(ranked.get(index).meritScore());
        }
    }

    @Test
    @DisplayName("clear empties the queue")
    void clearEmpties() {
        queue.insert(withScore(1, "A", 10.0));
        queue.insert(withScore(2, "B", 20.0));

        queue.clear();

        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.peek()).isNull();
        assertThat(queue.heapArraySnapshot()).isEmpty();
    }

    @Test
    @DisplayName("height stays logarithmic in the number of stored applicants")
    void heightIsLogarithmic() {
        for (int index = 0; index < 100; index++) {
            queue.insert(withScore(index + 1, "Applicant " + index, index));
        }
        assertThat(queue.height()).isEqualTo(7);
        assertThat(queue.height()).isLessThanOrEqualTo((int) (Math.log(100) / Math.log(2)) + 1);
    }

    @Test
    @DisplayName("the heap property survives a long random mix of insertions and extractions")
    void randomisedStressKeepsHeapValid() {
        Random random = new Random(20260921L);
        long sequence = 0;
        List<Double> expected = new ArrayList<>();

        for (int step = 0; step < 2_000; step++) {
            if (queue.isEmpty() || random.nextInt(100) < 60) {
                double score = MeritScorer.round(random.nextDouble() * 100);
                sequence++;
                queue.insert(withScore(sequence, "Applicant " + sequence, score));
                expected.add(score);
            } else {
                double removed = queue.extractMax().applicant().meritScore();
                double highest = expected.stream().max(Double::compare).orElseThrow();
                assertThat(removed).isEqualTo(highest);
                expected.remove(Double.valueOf(highest));
            }
            assertThat(queue.satisfiesHeapProperty()).isTrue();
            assertThat(queue.size()).isEqualTo(expected.size());
        }
    }
}
