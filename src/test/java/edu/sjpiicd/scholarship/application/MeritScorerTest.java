package edu.sjpiicd.scholarship.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MeritScorerTest {

    @Test
    @DisplayName("a perfect application scores 100")
    void perfectApplication() {
        MeritBreakdown breakdown = MeritScorer.score(1.00, 0, 24);

        assertThat(breakdown.academicPoints()).isEqualTo(60.0);
        assertThat(breakdown.needPoints()).isEqualTo(30.0);
        assertThat(breakdown.loadPoints()).isEqualTo(10.0);
        assertThat(breakdown.total()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("an application at every cut-off scores zero")
    void applicationAtEveryCutoff() {
        MeritBreakdown breakdown = MeritScorer.score(3.00, 60_000, 0);

        assertThat(breakdown.academicPoints()).isZero();
        assertThat(breakdown.needPoints()).isZero();
        assertThat(breakdown.loadPoints()).isZero();
        assertThat(breakdown.total()).isZero();
    }

    @Test
    @DisplayName("values past a cut-off clamp to zero instead of going negative")
    void componentsClampAtZero() {
        MeritBreakdown breakdown = MeritScorer.score(4.50, 250_000, 1);

        assertThat(breakdown.academicPoints()).isZero();
        assertThat(breakdown.needPoints()).isZero();
        assertThat(breakdown.loadPoints()).isGreaterThan(0.0);
        assertThat(breakdown.total()).isEqualTo(breakdown.loadPoints());
    }

    @Test
    @DisplayName("an overload of units still earns only the maximum load points")
    void loadPointsClampAtMaximum() {
        assertThat(MeritScorer.score(2.00, 20_000, 36).loadPoints()).isEqualTo(10.0);
        assertThat(MeritScorer.score(2.00, 20_000, 24).loadPoints()).isEqualTo(10.0);
    }

    @ParameterizedTest(name = "GWA {0} earns {1} academic points")
    @CsvSource({"1.00, 60.00", "1.50, 45.00", "2.00, 30.00", "2.50, 15.00", "3.00, 0.00"})
    @DisplayName("academic points fall linearly from 1.00 to the 3.00 cut-off")
    void academicPointsAreLinear(double gwa, double expected) {
        assertThat(MeritScorer.score(gwa, 60_000, 0).academicPoints()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "income {0} earns {1} need points")
    @CsvSource({"0, 30.00", "15000, 22.50", "30000, 15.00", "45000, 7.50", "60000, 0.00"})
    @DisplayName("need points fall linearly from zero income to the ceiling")
    void needPointsAreLinear(double income, double expected) {
        assertThat(MeritScorer.score(3.00, income, 0).needPoints()).isEqualTo(expected);
    }

    @Test
    @DisplayName("the components always add up to the reported total")
    void componentsSumToTotal() {
        MeritBreakdown breakdown = MeritScorer.score(1.83, 17_400, 19);
        double sum = breakdown.academicPoints() + breakdown.needPoints() + breakdown.loadPoints();

        assertThat(breakdown.total()).isCloseTo(sum, within(0.005));
        assertThat(breakdown.total()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("scoring is deterministic")
    void scoringIsDeterministic() {
        assertThat(MeritScorer.score(2.25, 28_500, 21))
                .isEqualTo(MeritScorer.score(2.25, 28_500, 21));
    }

    @Test
    @DisplayName("a better applicant never scores lower than a worse one")
    void betterApplicantsScoreHigher() {
        double better = MeritScorer.score(1.50, 10_000, 24).total();
        double worse = MeritScorer.score(2.50, 40_000, 15).total();

        assertThat(better).isGreaterThan(worse);
    }
}
