package edu.sjpiicd.scholarship.application;

/**
 * The JSON shape of one applicant.
 *
 * <p>{@code rank} is the 1-based position in the fully ordered ranking and {@code heapIndex} is the
 * applicant's actual slot in the backing array. They differ for every applicant except the root,
 * which is exactly what makes a heap cheaper to maintain than a sorted list; the interface shows
 * both side by side.
 */
public record ApplicantView(
        long sequence,
        String referenceCode,
        String name,
        String program,
        double gwa,
        double monthlyIncome,
        int unitsEnrolled,
        double academicPoints,
        double needPoints,
        double loadPoints,
        double meritScore,
        Integer rank,
        Integer heapIndex) {

    public static ApplicantView of(Applicant applicant, Integer rank, Integer heapIndex) {
        return new ApplicantView(
                applicant.sequence(),
                applicant.referenceCode(),
                applicant.name(),
                applicant.program(),
                applicant.gwa(),
                applicant.monthlyIncome(),
                applicant.unitsEnrolled(),
                applicant.merit().academicPoints(),
                applicant.merit().needPoints(),
                applicant.merit().loadPoints(),
                applicant.meritScore(),
                rank,
                heapIndex);
    }
}
