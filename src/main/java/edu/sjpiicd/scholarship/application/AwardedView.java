package edu.sjpiicd.scholarship.application;

/**
 * One granted scholarship slot.
 *
 * @param slotNumber the slot this applicant received, starting at 1
 * @param applicant  the applicant who was removed from the root of the heap
 */
public record AwardedView(int slotNumber, ApplicantView applicant) {
}
