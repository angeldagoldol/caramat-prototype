package edu.sjpiicd.scholarship.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.sjpiicd.scholarship.application.ScholarshipService.QueueOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScholarshipServiceTest {

    private static ApplicationRequest request(String name, double gwa, double income, int units) {
        return new ApplicationRequest(name, "BS Information Technology", gwa, income, units);
    }

    @Test
    @DisplayName("a fresh service reports an empty queue with every slot open")
    void freshServiceIsEmpty() {
        QueueReport report = new ScholarshipService(5).report();

        assertThat(report.waitingCount()).isZero();
        assertThat(report.totalSubmitted()).isZero();
        assertThat(report.totalSlots()).isEqualTo(5);
        assertThat(report.slotsRemaining()).isEqualTo(5);
        assertThat(report.nextInLine()).isNull();
        assertThat(report.waiting()).isEmpty();
        assertThat(report.heapArray()).isEmpty();
        assertThat(report.awarded()).isEmpty();
        assertThat(report.heapValid()).isTrue();
    }

    @Test
    @DisplayName("a slot count below one is rejected")
    void slotCountIsValidated() {
        assertThatThrownBy(() -> new ScholarshipService(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("submitting trims the text fields and assigns a reference code")
    void submitAssignsReferenceCode() {
        ScholarshipService service = new ScholarshipService(3);

        ActionResponse response = service.submit(
                new ApplicationRequest("  Maria Santos  ", "  BS Information Technology  ", 1.50, 12_000.0, 24));

        assertThat(response.subject().name()).isEqualTo("Maria Santos");
        assertThat(response.subject().program()).isEqualTo("BS Information Technology");
        assertThat(response.subject().referenceCode()).isEqualTo("SCH-0001");
        assertThat(response.subject().rank()).isEqualTo(1);
        assertThat(response.operation().operation()).isEqualTo("insert");
        assertThat(response.queue().totalSubmitted()).isEqualTo(1);
    }

    @Test
    @DisplayName("the strongest applicant holds the root regardless of arrival order")
    void strongestApplicantHoldsRoot() {
        ScholarshipService service = new ScholarshipService(3);
        service.submit(request("Weakest", 2.90, 55_000, 12));
        service.submit(request("Middle", 2.20, 30_000, 21));
        service.submit(request("Strongest", 1.10, 8_000, 24));
        service.submit(request("Also weak", 2.80, 50_000, 15));

        QueueReport report = service.report();

        assertThat(report.nextInLine().name()).isEqualTo("Strongest");
        assertThat(report.heapArray().get(0).name()).isEqualTo("Strongest");
        assertThat(report.waiting().get(0).name()).isEqualTo("Strongest");
        assertThat(report.heapValid()).isTrue();
    }

    @Test
    @DisplayName("awarding a slot removes the root and records the award")
    void awardRemovesRoot() {
        ScholarshipService service = new ScholarshipService(2);
        service.submit(request("Lower", 2.50, 40_000, 18));
        service.submit(request("Higher", 1.20, 6_000, 24));

        ActionResponse response = service.awardNextSlot();

        assertThat(response.subject().name()).isEqualTo("Higher");
        assertThat(response.operation().operation()).isEqualTo("extractMax");
        assertThat(response.queue().awarded()).hasSize(1);
        assertThat(response.queue().awarded().get(0).slotNumber()).isEqualTo(1);
        assertThat(response.queue().awarded().get(0).applicant().name()).isEqualTo("Higher");
        assertThat(response.queue().waitingCount()).isEqualTo(1);
        assertThat(response.queue().slotsRemaining()).isEqualTo(1);
        assertThat(response.queue().nextInLine().name()).isEqualTo("Lower");
    }

    @Test
    @DisplayName("awarding on an empty queue is rejected and changes nothing")
    void awardOnEmptyQueueRejected() {
        ScholarshipService service = new ScholarshipService(3);

        assertThatThrownBy(service::awardNextSlot)
                .isInstanceOf(QueueOperationException.class)
                .hasMessageContaining("No applications are waiting");
        assertThat(service.report().awarded()).isEmpty();
    }

    @Test
    @DisplayName("awarding past the configured slot count is rejected and the queue is kept intact")
    void awardStopsAtSlotLimit() {
        ScholarshipService service = new ScholarshipService(2);
        service.submit(request("First", 1.20, 5_000, 24));
        service.submit(request("Second", 1.40, 7_000, 24));
        service.submit(request("Third", 1.60, 9_000, 24));

        service.awardNextSlot();
        service.awardNextSlot();

        assertThatThrownBy(service::awardNextSlot)
                .isInstanceOf(QueueOperationException.class)
                .hasMessageContaining("already been awarded");

        QueueReport report = service.report();
        assertThat(report.slotsRemaining()).isZero();
        assertThat(report.waitingCount()).isEqualTo(1);
        assertThat(report.nextInLine().name()).isEqualTo("Third");
    }

    @Test
    @DisplayName("awards are granted in descending merit order")
    void awardsFollowMeritOrder() {
        ScholarshipService service = new ScholarshipService(4);
        service.submit(request("Fourth", 2.80, 50_000, 12));
        service.submit(request("First", 1.10, 4_000, 24));
        service.submit(request("Third", 2.40, 38_000, 18));
        service.submit(request("Second", 1.70, 15_000, 24));

        assertThat(service.awardNextSlot().subject().name()).isEqualTo("First");
        assertThat(service.awardNextSlot().subject().name()).isEqualTo("Second");
        assertThat(service.awardNextSlot().subject().name()).isEqualTo("Third");
        assertThat(service.awardNextSlot().subject().name()).isEqualTo("Fourth");
        assertThat(service.report().waitingCount()).isZero();
    }

    @Test
    @DisplayName("identical applications are awarded in submission order")
    void identicalApplicationsKeepSubmissionOrder() {
        ScholarshipService service = new ScholarshipService(3);
        service.submit(request("Applied first", 2.00, 20_000, 24));
        service.submit(request("Applied second", 2.00, 20_000, 24));
        service.submit(request("Applied third", 2.00, 20_000, 24));

        assertThat(service.awardNextSlot().subject().name()).isEqualTo("Applied first");
        assertThat(service.awardNextSlot().subject().name()).isEqualTo("Applied second");
        assertThat(service.awardNextSlot().subject().name()).isEqualTo("Applied third");
    }

    @Test
    @DisplayName("the ranked list and the raw heap array hold the same applicants in different orders")
    void rankedAndHeapViewsAgreeOnMembership() {
        ScholarshipService service = new ScholarshipService(5);
        service.submit(request("A", 2.90, 55_000, 12));
        service.submit(request("B", 1.30, 9_000, 24));
        service.submit(request("C", 2.10, 25_000, 21));
        service.submit(request("D", 1.80, 14_000, 24));
        service.submit(request("E", 2.60, 47_000, 15));

        QueueReport report = service.report();

        assertThat(report.waiting()).extracting(ApplicantView::name)
                .containsExactlyInAnyOrderElementsOf(
                        report.heapArray().stream().map(ApplicantView::name).toList());
        for (int index = 1; index < report.waiting().size(); index++) {
            assertThat(report.waiting().get(index - 1).meritScore())
                    .isGreaterThanOrEqualTo(report.waiting().get(index).meritScore());
        }
        assertThat(report.waiting().get(0).rank()).isEqualTo(1);
        assertThat(report.heapArray().get(0).heapIndex()).isZero();
    }

    @Test
    @DisplayName("reset clears the queue, the awards, and the reference counter")
    void resetClearsEverything() {
        ScholarshipService service = new ScholarshipService(3);
        service.submit(request("A", 1.50, 10_000, 24));
        service.awardNextSlot();

        QueueReport report = service.reset().queue();

        assertThat(report.waitingCount()).isZero();
        assertThat(report.awarded()).isEmpty();
        assertThat(report.totalSubmitted()).isZero();
        assertThat(report.slotsRemaining()).isEqualTo(3);
        assertThat(service.submit(request("New", 2.00, 20_000, 24)).subject().referenceCode())
                .isEqualTo("SCH-0001");
    }

    @Test
    @DisplayName("the sample set loads a full queue whose root is the strongest applicant")
    void sampleSetLoads() {
        ScholarshipService service = new ScholarshipService(5);

        QueueReport report = service.loadSampleApplications().queue();

        assertThat(report.waitingCount()).isEqualTo(7);
        assertThat(report.totalSubmitted()).isEqualTo(7);
        assertThat(report.nextInLine().name()).isEqualTo("Bea Angeline Navarro");
        assertThat(report.heapValid()).isTrue();
        for (int index = 1; index < report.waiting().size(); index++) {
            assertThat(report.waiting().get(index - 1).meritScore())
                    .isGreaterThanOrEqualTo(report.waiting().get(index).meritScore());
        }
    }

    @Test
    @DisplayName("the sample set contains a tie that is resolved by submission order")
    void sampleSetContainsResolvedTie() {
        ScholarshipService service = new ScholarshipService(5);
        service.loadSampleApplications();

        QueueReport report = service.report();
        ApplicantView andrea = report.waiting().stream()
                .filter(view -> view.name().equals("Andrea Faith Lim"))
                .findFirst()
                .orElseThrow();
        ApplicantView kyla = report.waiting().stream()
                .filter(view -> view.name().equals("Kyla Marie Ocampo"))
                .findFirst()
                .orElseThrow();

        assertThat(andrea.meritScore()).isEqualTo(kyla.meritScore());
        assertThat(andrea.sequence()).isLessThan(kyla.sequence());
        assertThat(andrea.rank()).isLessThan(kyla.rank());
    }

    @Test
    @DisplayName("the heap stays valid through a long mix of submissions and awards")
    void heapStaysValidUnderMixedTraffic() {
        ScholarshipService service = new ScholarshipService(500);
        for (int index = 0; index < 300; index++) {
            double gwa = 1.00 + ((index * 13) % 400) / 100.0;
            service.submit(request("Applicant " + index, Math.min(gwa, 5.00), (index * 977) % 90_000, 1 + (index % 36)));
            assertThat(service.report().heapValid()).isTrue();
            if (index % 3 == 0) {
                service.awardNextSlot();
                assertThat(service.report().heapValid()).isTrue();
            }
        }
        assertThat(service.report().waiting()).isSortedAccordingTo(
                (left, right) -> Double.compare(right.meritScore(), left.meritScore()));
    }
}
