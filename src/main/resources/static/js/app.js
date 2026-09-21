/**
 * Wires the browser interface to the JSON endpoints.
 *
 * The browser only collects input and renders what Java returns. Merit scoring, ordering, and the
 * choice of who is awarded next all happen inside the heap on the server.
 */
import { createApi, ApiError } from './api.js';
import {
  buildTreeLevels,
  collectFieldErrors,
  describeError,
  describeQueue,
  formatGwa,
  formatPeso,
  formatScore,
  parentIndexOf,
} from './heap-view.js';

const api = createApi();

const FIELDS = ['name', 'program', 'gwa', 'monthlyIncome', 'unitsEnrolled'];

const el = {
  form: document.getElementById('application-form'),
  sampleButton: document.getElementById('sample-button'),
  resetButton: document.getElementById('reset-button'),
  awardButton: document.getElementById('award-button'),
  status: document.getElementById('status-message'),
  slotsRemaining: document.getElementById('slots-remaining'),
  waitingCount: document.getElementById('waiting-count'),
  nextCard: document.getElementById('next-card'),
  operationCard: document.getElementById('operation-card'),
  opName: document.getElementById('op-name'),
  opComparisons: document.getElementById('op-comparisons'),
  opSwaps: document.getElementById('op-swaps'),
  opHeight: document.getElementById('op-height'),
  opSteps: document.getElementById('op-steps'),
  opEmpty: document.getElementById('op-empty'),
  heapCapacity: document.getElementById('heap-capacity'),
  heapSize: document.getElementById('heap-size'),
  heapHeight: document.getElementById('heap-height'),
  heapValid: document.getElementById('heap-valid'),
  heapTree: document.getElementById('heap-tree'),
  heapArrayBody: document.getElementById('heap-array-body'),
  heapArrayEmpty: document.getElementById('heap-array-empty'),
  rankedBody: document.getElementById('ranked-body'),
  rankedEmpty: document.getElementById('ranked-empty'),
  awardedBody: document.getElementById('awarded-body'),
  awardedEmpty: document.getElementById('awarded-empty'),
};

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

function setStatus(message, tone = 'info') {
  el.status.textContent = message;
  el.status.classList.remove('status--error', 'status--ok');
  if (tone === 'error') {
    el.status.classList.add('status--error');
  } else if (tone === 'ok') {
    el.status.classList.add('status--ok');
  }
}

function clearFieldErrors() {
  for (const field of FIELDS) {
    const input = document.getElementById(field);
    const error = document.getElementById(`${field}-error`);
    input.removeAttribute('aria-invalid');
    error.hidden = true;
    error.textContent = '';
  }
}

function showFieldErrors(errors) {
  let firstInvalid = null;
  for (const field of FIELDS) {
    const message = errors[field];
    if (!message) {
      continue;
    }
    const input = document.getElementById(field);
    const error = document.getElementById(`${field}-error`);
    input.setAttribute('aria-invalid', 'true');
    error.textContent = message;
    error.hidden = false;
    if (!firstInvalid) {
      firstInvalid = input;
    }
  }
  if (firstInvalid) {
    firstInvalid.focus();
  }
}

function renderOperation(operation, report) {
  if (!operation) {
    el.operationCard.hidden = true;
    return;
  }
  el.operationCard.hidden = false;
  el.opName.textContent = operation.operation;
  el.opComparisons.textContent = operation.comparisons;
  el.opSwaps.textContent = operation.swaps;
  el.opHeight.textContent = report ? report.heapHeight : '—';

  el.opSteps.replaceChildren();
  const steps = operation.steps ?? [];
  for (const step of steps) {
    const item = document.createElement('li');
    item.textContent = step;
    el.opSteps.append(item);
  }
  el.opEmpty.hidden = steps.length > 0;
}

function renderNextCard(next) {
  if (!next) {
    el.nextCard.className = 'next-card next-card--empty';
    el.nextCard.textContent = 'Nothing is waiting.';
    el.awardButton.disabled = true;
    return;
  }
  el.nextCard.className = 'next-card';
  el.nextCard.replaceChildren();

  const name = document.createElement('div');
  name.className = 'next-card__name';
  name.textContent = `${next.name} (${next.referenceCode})`;

  const meta = document.createElement('div');
  meta.className = 'next-card__meta';
  meta.textContent = `${next.program} · GWA ${formatGwa(next.gwa)} · ${formatPeso(next.monthlyIncome)}/month · ${next.unitsEnrolled} units`;

  const score = document.createElement('div');
  score.className = 'next-card__score';
  score.textContent = formatScore(next.meritScore);
  const label = document.createElement('span');
  label.textContent = ' merit points';
  score.append(label);

  el.nextCard.append(name, meta, score);
}

function renderHeapTree(heapArray) {
  el.heapTree.replaceChildren();
  const levels = buildTreeLevels(heapArray);
  if (levels.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'heap-empty';
    empty.textContent = 'The tree is empty.';
    el.heapTree.append(empty);
    return;
  }
  for (const level of levels) {
    const row = document.createElement('div');
    row.className = 'heap-tree__level';
    for (const node of level) {
      const box = document.createElement('div');
      box.className = node.index === 0 ? 'heap-node heap-node--root' : 'heap-node';

      const index = document.createElement('span');
      index.className = 'heap-node__index';
      index.textContent = `index ${node.index}`;

      const name = document.createElement('span');
      name.className = 'heap-node__name';
      name.textContent = node.applicant.name;

      const score = document.createElement('span');
      score.className = 'heap-node__score';
      score.textContent = formatScore(node.applicant.meritScore);

      box.append(index, name, document.createElement('br'), score);
      row.append(box);
    }
    el.heapTree.append(row);
  }
}

function cell(text, className) {
  const td = document.createElement('td');
  td.textContent = text;
  if (className) {
    td.className = className;
  }
  return td;
}

function renderHeapArray(heapArray) {
  el.heapArrayBody.replaceChildren();
  el.heapArrayEmpty.hidden = heapArray.length > 0;
  for (const applicant of heapArray) {
    const row = document.createElement('tr');
    if (applicant.heapIndex === 0) {
      row.className = 'is-root';
    }
    const parent = parentIndexOf(applicant.heapIndex);
    row.append(
      cell(String(applicant.heapIndex), 'rank-cell'),
      cell(parent === null ? 'root' : String(parent)),
      cell(`${applicant.name} (${applicant.referenceCode})`),
      cell(formatScore(applicant.meritScore), 'numeric'),
    );
    el.heapArrayBody.append(row);
  }
}

function renderRanked(waiting) {
  el.rankedBody.replaceChildren();
  el.rankedEmpty.hidden = waiting.length > 0;
  for (const applicant of waiting) {
    const row = document.createElement('tr');
    if (applicant.rank === 1) {
      row.className = 'is-root';
    }
    row.append(
      cell(String(applicant.rank), 'rank-cell'),
      cell(applicant.referenceCode),
      cell(applicant.name),
      cell(applicant.program),
      cell(formatGwa(applicant.gwa), 'numeric'),
      cell(formatPeso(applicant.monthlyIncome), 'numeric'),
      cell(String(applicant.unitsEnrolled), 'numeric'),
      cell(formatScore(applicant.academicPoints), 'numeric'),
      cell(formatScore(applicant.needPoints), 'numeric'),
      cell(formatScore(applicant.loadPoints), 'numeric'),
      cell(formatScore(applicant.meritScore), 'numeric'),
    );
    el.rankedBody.append(row);
  }
}

function renderAwarded(awarded) {
  el.awardedBody.replaceChildren();
  el.awardedEmpty.hidden = awarded.length > 0;
  for (const slot of awarded) {
    const row = document.createElement('tr');
    row.append(
      cell(String(slot.slotNumber), 'rank-cell'),
      cell(slot.applicant.referenceCode),
      cell(slot.applicant.name),
      cell(slot.applicant.program),
      cell(formatScore(slot.applicant.meritScore), 'numeric'),
    );
    el.awardedBody.append(row);
  }
}

function renderReport(report) {
  el.slotsRemaining.textContent = `${report.slotsRemaining}/${report.totalSlots}`;
  el.waitingCount.textContent = report.waitingCount;
  el.heapCapacity.textContent = report.heapCapacity;
  el.heapSize.textContent = report.waitingCount;
  el.heapHeight.textContent = report.heapHeight;
  el.heapValid.textContent = report.heapValid ? 'Valid' : 'BROKEN';

  renderNextCard(report.nextInLine);
  renderHeapTree(report.heapArray);
  renderHeapArray(report.heapArray);
  renderRanked(report.waiting);
  renderAwarded(report.awarded);

  el.awardButton.disabled = report.waitingCount === 0 || report.slotsRemaining === 0;
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

function applyAction(response, tone = 'ok') {
  renderReport(response.queue);
  renderOperation(response.operation, response.queue);
  setStatus(response.message, tone);
}

function handleFailure(error, fallback) {
  if (error instanceof ApiError) {
    setStatus(describeError(error.payload, fallback), 'error');
    return;
  }
  setStatus(`${fallback} The server could not be reached.`, 'error');
}

async function runGuarded(button, work, fallback) {
  const wasDisabled = button.disabled;
  button.disabled = true;
  try {
    await work();
  } catch (error) {
    handleFailure(error, fallback);
  } finally {
    button.disabled = wasDisabled;
  }
}

el.form.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearFieldErrors();

  const values = {
    name: el.form.name.value,
    program: el.form.program.value,
    gwa: el.form.gwa.value,
    monthlyIncome: el.form.monthlyIncome.value,
    unitsEnrolled: el.form.unitsEnrolled.value,
  };

  const errors = collectFieldErrors(values);
  if (Object.keys(errors).length > 0) {
    showFieldErrors(errors);
    setStatus('Fix the highlighted fields. The queue was not changed.', 'error');
    return;
  }

  const submitButton = el.form.querySelector('button[type="submit"]');
  await runGuarded(
    submitButton,
    async () => {
      const response = await api.submitApplication({
        name: values.name.trim(),
        program: values.program.trim(),
        gwa: Number(values.gwa),
        monthlyIncome: Number(values.monthlyIncome),
        unitsEnrolled: Number(values.unitsEnrolled),
      });
      applyAction(response);
      el.form.reset();
      el.form.name.focus();
    },
    'The application was not added.',
  );
});

el.awardButton.addEventListener('click', () => {
  runGuarded(el.awardButton, async () => applyAction(await api.awardSlot()), 'No slot was awarded.');
});

el.sampleButton.addEventListener('click', () => {
  runGuarded(
    el.sampleButton,
    async () => {
      clearFieldErrors();
      applyAction(await api.loadSample(), 'info');
    },
    'The sample set was not loaded.',
  );
});

el.resetButton.addEventListener('click', () => {
  runGuarded(
    el.resetButton,
    async () => {
      clearFieldErrors();
      applyAction(await api.resetQueue(), 'info');
    },
    'The queue was not cleared.',
  );
});

// ---------------------------------------------------------------------------
// First paint
// ---------------------------------------------------------------------------

api
  .fetchQueue()
  .then((report) => {
    renderReport(report);
    setStatus(describeQueue(report));
  })
  .catch((error) => handleFailure(error, 'The queue could not be loaded.'));
