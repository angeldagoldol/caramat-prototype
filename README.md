# Online Scholarship Application System

A working prototype for the CC104 Mini-System Project. Scholarship applications are held in a
**binary max-heap priority queue written from scratch** in
[`ApplicantPriorityQueue.java`](src/main/java/edu/sjpiicd/scholarship/application/ApplicantPriorityQueue.java),
not in `java.util.PriorityQueue`. The applicant with the highest merit score is always at the root,
so awarding a scholarship slot is a single `extractMax` and never a re-sort.

A Spring Boot service owns the heap, exposes a small JSON API, and serves the browser interface from
the same process. The repository also carries the SJPIIC/CICT **Chapters I-II** paper and the script
that builds it.

> **Scope.** This submission covers the prototype plus Chapters I and II only. Chapter III onward is
> scheduled for the succeeding laboratory examinations and is deliberately not written yet.

## Why a priority queue

A scholarship office never needs the applications kept in full sorted order. It needs one answer,
repeatedly: *who is the most deserving applicant still waiting?* A max-heap answers exactly that in
constant time while keeping insertions and removals cheap.

| Operation | What it does | Cost |
| --- | --- | --- |
| `insert` | appends at the last array index, then sifts up while it outranks its parent | O(log n) |
| `extractMax` | removes the root, moves the last element up, then sifts down | O(log n) |
| `peek` | reads the root | O(1) |
| `rankedSnapshot` | heap sort over a **copy**, so the live queue is untouched | O(n log n) |

The heap is a complete binary tree flattened into a plain array. For the element at index `i` the
parent is at `(i - 1) / 2` and the children are at `2i + 1` and `2i + 2`; no node links are stored.

### Merit score

Each application is reduced to one number from 0 to 100 by
[`MeritScorer`](src/main/java/edu/sjpiicd/scholarship/application/MeritScorer.java):

| Component | Weight | Full marks at | Zero at |
| --- | --- | --- | --- |
| Academic standing (GWA) | 60 | 1.00 | 3.00 or worse |
| Financial need (monthly household income) | 30 | ₱0 | ₱60,000 or more |
| Enrolled load (units) | 10 | 24 units | 0 units |

Exact ties are broken in favour of whoever submitted first, which makes the ordering total and
deterministic — applying earlier is never a disadvantage against an identically qualified candidate.

## Features

- Submit an application with a name, degree program, GWA, monthly household income, and units.
- Validate every field in Java; a rejected request never reaches the heap and never changes the queue.
- Score each application and show the three components beside the total.
- Insert with a sift-up so the highest merit applicant always holds the root.
- Award a slot with one `extractMax` and sift-down, subject to a configurable slot limit (default 5).
- Inspect the **live backing array** in level order with each element's index and parent index.
- Inspect the same array drawn as the **complete binary tree**, with the root highlighted.
- Read a **fully ranked** waiting list produced by heap sort over a copy.
- See the **comparisons and swaps** each operation performed, plus a readable trace of every swap.
- Re-check of the heap property across the whole array after every operation.
- Load a sample set (including a late-arriving strongest applicant and an exact tie) or clear the queue.

The array is deliberately *not* sorted. Only the root is guaranteed to be the maximum — the interface
makes that visible, which is the whole point of the structure.

## Prerequisites

- Java 17 or newer
- Node.js 20 or newer, for the dependency-free frontend tests and the smoke test
- Python 3 with `python-docx`, `Pillow`, and `pytest`, only when rebuilding or testing the paper
- LibreOffice Writer, only when exporting the paper to PDF

Maven 3.9.11 comes with the Maven Wrapper, so no separate Maven install is needed. The frontend uses
only browser and Node built-ins — there is no `npm install` step.

## Run locally

```bash
./mvnw spring-boot:run          # macOS / Linux
```

```powershell
.\mvnw.cmd spring-boot:run      # Windows PowerShell
```

Then open <http://localhost:8080>. To use another port: `PORT=18080 ./mvnw spring-boot:run`.

To change the number of scholarship slots: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--scholarship.slots=3`

> Applications live in the memory of the running Java process. Every restart or redeploy clears the
> queue and the awarded slots.

## Tests

```bash
./mvnw test              # 59 Java tests: heap, scorer, service, HTTP endpoints
npm run test:frontend    # 29 Node tests: pure view helpers and the API client
python3 -m pytest tests/ -q   # 24 tests: structure of the generated paper
```

End-to-end, against a real running server:

```bash
./mvnw package -DskipTests
npm run test:smoke       # 13 checks: boots the jar and drives the live API
```

The Java suite includes a randomised stress test that runs 2,000 mixed insertions and extractions and
re-verifies the heap property after every single one.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/applications` | Score an application and insert it into the heap |
| `POST` | `/api/awards` | Remove the root and grant a scholarship slot |
| `GET` | `/api/queue` | Read the current queue without changing it |
| `POST` | `/api/sample` | Replace the queue with the demonstration set |
| `POST` | `/api/reset` | Clear the queue and the awarded slots |

Every mutating endpoint returns the message, the applicant acted on, the heap operation trace, and
the full queue state.

## Layout

```
src/main/java/edu/sjpiicd/scholarship/
├── ScholarshipSystemApplication.java   Spring Boot entry point
├── application/
│   ├── ApplicantPriorityQueue.java     the binary max-heap — the core of the system
│   ├── MeritScorer.java                turns an application into one comparable number
│   ├── Applicant.java                  the element stored in the heap
│   ├── ScholarshipService.java         owns the heap and the awarded slots
│   ├── ScholarshipController.java      the JSON endpoints
│   └── …                               request and response records
└── error/                              consistent JSON for rejected requests

src/main/resources/static/              the browser interface (no build step, no framework)
scripts/build_chapters_1_2_docx.py      builds the paper and draws the flowchart from code
scripts/export-pdf.sh                   exports the paper to PDF
scripts/smoke-test.mjs                  end-to-end checks against a running server
assets/                                 SJPIICD logo, generated flowchart, screenshots
deliverables/                           the Chapters I-II paper, .docx and .pdf
tests/                                  structural tests for the generated paper
```

## Rebuilding the paper

```bash
python3 scripts/build_chapters_1_2_docx.py
./scripts/export-pdf.sh
```

The flowchart is drawn from code rather than pasted in, so the figure in the paper cannot drift away
from the system it documents. The cover page fields (researchers, group leader, section, date) are
left as placeholders — fill them in before submitting.

## Deploying

`Dockerfile` and `render.yaml` are included for a container deploy. The health check path is
`/api/queue`.
