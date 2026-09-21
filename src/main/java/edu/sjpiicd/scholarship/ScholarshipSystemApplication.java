package edu.sjpiicd.scholarship;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Online Scholarship Application System prototype.
 *
 * <p>The system stores every submitted scholarship application inside a binary max-heap
 * priority queue that is implemented by hand in
 * {@link edu.sjpiicd.scholarship.application.ApplicantPriorityQueue}. Awarding a slot always
 * removes the applicant with the highest merit score, which is the root of that heap.
 */
@SpringBootApplication
public class ScholarshipSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScholarshipSystemApplication.class, args);
    }
}
