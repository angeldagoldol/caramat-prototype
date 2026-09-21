package edu.sjpiicd.scholarship.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts the raw fields of a scholarship application into a single merit score from 0 to 100.
 *
 * <p>The priority queue needs one comparable number per applicant. This class produces that number
 * from three weighted components so the ranking stays explainable during checking:
 *
 * <ul>
 *   <li><strong>Academic (60 points)</strong> - a general weighted average of 1.00 earns the full
 *       60 points and a general weighted average at or beyond the 3.00 cut-off earns 0.</li>
 *   <li><strong>Financial need (30 points)</strong> - a reported monthly household income of 0 earns
 *       the full 30 points and an income at or beyond the 60,000 peso ceiling earns 0.</li>
 *   <li><strong>Enrolled load (10 points)</strong> - 24 units or more earns the full 10 points and
 *       the points fall proportionally for a lighter load.</li>
 * </ul>
 *
 * <p>The scorer is deterministic and has no state, so the same application always produces the same
 * score. Ties on the total are broken by submission order inside the priority queue itself.
 */
public final class MeritScorer {

    /** Maximum points awarded for academic standing. */
    public static final double ACADEMIC_WEIGHT = 60.0;

    /** Maximum points awarded for financial need. */
    public static final double NEED_WEIGHT = 30.0;

    /** Maximum points awarded for the enrolled unit load. */
    public static final double LOAD_WEIGHT = 10.0;

    /** The best possible general weighted average on the 1.00 to 5.00 scale. */
    public static final double GWA_BEST = 1.00;

    /** The general weighted average at which academic points reach 0. */
    public static final double GWA_CUTOFF = 3.00;

    /** The monthly household income in pesos at which need points reach 0. */
    public static final double INCOME_CEILING = 60_000.0;

    /** The enrolled unit load that earns the full load points. */
    public static final double FULL_LOAD_UNITS = 24.0;

    private MeritScorer() {
    }

    /**
     * Scores one application.
     *
     * @param gwa            general weighted average, 1.00 (highest) through 5.00 (lowest)
     * @param monthlyIncome  reported monthly household income in pesos, 0 or greater
     * @param unitsEnrolled  units the applicant is enrolled in for the term, 1 or greater
     * @return the three components and their rounded total
     */
    public static MeritBreakdown score(double gwa, double monthlyIncome, int unitsEnrolled) {
        double academic = round(ratio(GWA_CUTOFF - gwa, GWA_CUTOFF - GWA_BEST) * ACADEMIC_WEIGHT);
        double need = round(ratio(INCOME_CEILING - monthlyIncome, INCOME_CEILING) * NEED_WEIGHT);
        double load = round(ratio(unitsEnrolled, FULL_LOAD_UNITS) * LOAD_WEIGHT);
        return new MeritBreakdown(academic, need, load, round(academic + need + load));
    }

    /** Returns {@code numerator / denominator} clamped to the 0 through 1 range. */
    private static double ratio(double numerator, double denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        double value = numerator / denominator;
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }

    /** Rounds to two decimal places using half-up rounding so displayed points always add up. */
    public static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
