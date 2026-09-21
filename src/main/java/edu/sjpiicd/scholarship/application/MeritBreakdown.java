package edu.sjpiicd.scholarship.application;

/**
 * The three weighted components that add up to an applicant's merit score.
 *
 * <p>Keeping the breakdown next to the total lets the interface show <em>why</em> one applicant
 * outranks another, which matters because the priority queue orders applicants by that total.
 *
 * @param academicPoints points earned from the general weighted average, 0 through 60
 * @param needPoints     points earned from monthly household income, 0 through 30
 * @param loadPoints     points earned from the enrolled unit load, 0 through 10
 * @param total          the sum of the three components, 0 through 100, rounded to two decimals
 */
public record MeritBreakdown(double academicPoints, double needPoints, double loadPoints, double total) {
}
