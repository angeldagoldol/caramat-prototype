package edu.sjpiicd.scholarship.application;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON endpoints the browser calls. All queue work is delegated to {@link ScholarshipService}. */
@RestController
@RequestMapping("/api")
public class ScholarshipController {

    private final ScholarshipService service;

    public ScholarshipController(ScholarshipService service) {
        this.service = service;
    }

    /** Scores an application and inserts it into the heap. */
    @PostMapping("/applications")
    public ResponseEntity<ActionResponse> submit(@Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(request));
    }

    /** Removes the root of the heap and records the awarded slot. */
    @PostMapping("/awards")
    public ActionResponse award() {
        return service.awardNextSlot();
    }

    /** Returns the current queue without changing it. */
    @GetMapping("/queue")
    public QueueReport queue() {
        return service.report();
    }

    /** Clears the queue and the awarded slots. */
    @PostMapping("/reset")
    public ActionResponse reset() {
        return service.reset();
    }

    /** Replaces the queue with a fixed demonstration set. */
    @PostMapping("/sample")
    public ActionResponse sample() {
        return service.loadSampleApplications();
    }
}
