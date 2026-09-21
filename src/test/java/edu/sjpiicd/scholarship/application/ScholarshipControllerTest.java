package edu.sjpiicd.scholarship.application;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = "scholarship.slots=2")
class ScholarshipControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ScholarshipService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        service.reset();
    }

    private static String body(String name, double gwa, double income, int units) {
        return """
                {"name":"%s","program":"BS Information Technology","gwa":%s,"monthlyIncome":%s,"unitsEnrolled":%d}
                """.formatted(name, gwa, income, units);
    }

    @Test
    @DisplayName("GET /api/queue returns an empty report before anything is submitted")
    void emptyQueue() throws Exception {
        mockMvc.perform(get("/api/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingCount").value(0))
                .andExpect(jsonPath("$.totalSlots").value(2))
                .andExpect(jsonPath("$.slotsRemaining").value(2))
                .andExpect(jsonPath("$.nextInLine").doesNotExist())
                .andExpect(jsonPath("$.heapValid").value(true));
    }

    @Test
    @DisplayName("POST /api/applications returns 201 with the insert trace")
    void submitReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Maria Santos", 1.50, 12000, 24)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject.name").value("Maria Santos"))
                .andExpect(jsonPath("$.subject.referenceCode").value("SCH-0001"))
                .andExpect(jsonPath("$.operation.operation").value("insert"))
                .andExpect(jsonPath("$.queue.waitingCount").value(1))
                .andExpect(jsonPath("$.queue.nextInLine.name").value("Maria Santos"));
    }

    @Test
    @DisplayName("a blank name is rejected with 400 and the queue is unchanged")
    void blankNameRejected() throws Exception {
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", 1.50, 12000, 24)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0]", containsString("Applicant name is required")));

        mockMvc.perform(get("/api/queue")).andExpect(jsonPath("$.waitingCount").value(0));
    }

    @Test
    @DisplayName("an out-of-range general weighted average is rejected with 400")
    void outOfRangeGwaRejected() throws Exception {
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Maria Santos", 6.00, 12000, 24)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("5.00 or lower")));
    }

    @Test
    @DisplayName("an out-of-range unit load is rejected with 400")
    void outOfRangeUnitsRejected() throws Exception {
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Maria Santos", 1.50, 12000, 99)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("36 or fewer")));
    }

    @Test
    @DisplayName("malformed JSON is rejected with 400")
    void malformedJsonRejected() throws Exception {
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("could not be read")));
    }

    @Test
    @DisplayName("POST /api/awards grants the highest merit applicant")
    void awardGrantsHighestMerit() throws Exception {
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content(body("Lower", 2.60, 48000, 15)));
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content(body("Higher", 1.20, 6000, 24)));

        mockMvc.perform(post("/api/awards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject.name").value("Higher"))
                .andExpect(jsonPath("$.operation.operation").value("extractMax"))
                .andExpect(jsonPath("$.queue.awarded[0].slotNumber").value(1))
                .andExpect(jsonPath("$.queue.slotsRemaining").value(1));
    }

    @Test
    @DisplayName("awarding an empty queue returns 409")
    void awardOnEmptyQueueConflicts() throws Exception {
        mockMvc.perform(post("/api/awards"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("No applications are waiting")));
    }

    @Test
    @DisplayName("awarding past the slot limit returns 409")
    void awardPastSlotLimitConflicts() throws Exception {
        for (int index = 0; index < 3; index++) {
            mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                    .content(body("Applicant " + index, 1.50 + index * 0.1, 10000 + index, 24)));
        }
        mockMvc.perform(post("/api/awards")).andExpect(status().isOk());
        mockMvc.perform(post("/api/awards")).andExpect(status().isOk());

        mockMvc.perform(post("/api/awards"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already been awarded")));
    }

    @Test
    @DisplayName("POST /api/sample loads the demonstration queue")
    void sampleLoads() throws Exception {
        mockMvc.perform(post("/api/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.waitingCount").value(7))
                .andExpect(jsonPath("$.queue.nextInLine.name").value("Bea Angeline Navarro"))
                .andExpect(jsonPath("$.queue.heapValid").value(true));
    }

    @Test
    @DisplayName("POST /api/reset empties the queue")
    void resetEmptiesQueue() throws Exception {
        mockMvc.perform(post("/api/sample"));

        mockMvc.perform(post("/api/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.waitingCount").value(0))
                .andExpect(jsonPath("$.queue.totalSubmitted").value(0));
    }

    @Test
    @DisplayName("the interface is served from the same application")
    void interfaceIsServed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }
}
