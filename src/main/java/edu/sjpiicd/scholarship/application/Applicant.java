package edu.sjpiicd.scholarship.application;

/**
 * One submitted scholarship application.
 *
 * <p>This is the element type stored inside the binary max-heap. Two fields drive the ordering:
 * {@code merit.total()} is the primary key and {@code sequence} is the tie-breaker, so applicants
 * with identical merit scores are still released in the order they applied.
 *
 * @param sequence       submission order, starting at 1 and never reused
 * @param referenceCode  human-readable identifier shown in the interface, e.g. {@code SCH-0007}
 * @param name           applicant's full name
 * @param program        degree program the applicant is enrolled in
 * @param gwa            general weighted average, 1.00 through 5.00
 * @param monthlyIncome  reported monthly household income in pesos
 * @param unitsEnrolled  units enrolled for the current term
 * @param merit          the weighted score components and their total
 */
public record Applicant(
        long sequence,
        String referenceCode,
        String name,
        String program,
        double gwa,
        double monthlyIncome,
        int unitsEnrolled,
        MeritBreakdown merit) {

    /** Builds an applicant and scores it in one step. */
    public static Applicant of(
            long sequence, String name, String program, double gwa, double monthlyIncome, int unitsEnrolled) {
        return new Applicant(
                sequence,
                String.format("SCH-%04d", sequence),
                name,
                program,
                gwa,
                monthlyIncome,
                unitsEnrolled,
                MeritScorer.score(gwa, monthlyIncome, unitsEnrolled));
    }

    /** Convenience accessor for the value the heap orders on. */
    public double meritScore() {
        return merit.total();
    }
}
