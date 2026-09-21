/**
 * Pure helpers shared by the interface and the Node test suite.
 *
 * Nothing in this module touches the DOM or the network, so every function here can be unit tested
 * directly. The rules mirror the Java side: the browser never decides ranking, it only formats what
 * the heap already returned.
 */

/** Peso amounts, grouped and without decimals. */
export function formatPeso(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  return `₱${Number(value).toLocaleString('en-PH', { maximumFractionDigits: 0 })}`;
}

/** Merit scores and point components, always to two decimals. */
export function formatScore(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '—';
  }
  return Number(value).toFixed(2);
}

/** General weighted averages, always to two decimals on the 1.00 to 5.00 scale. */
export function formatGwa(value) {
  return formatScore(value);
}

/**
 * Index of the parent of `index` in a binary heap, or null for the root.
 * Mirrors ApplicantPriorityQueue.parentOf.
 */
export function parentIndexOf(index) {
  if (!Number.isInteger(index) || index <= 0) {
    return null;
  }
  return Math.floor((index - 1) / 2);
}

/** Indices of the two children of `index`, mirroring the Java index arithmetic. */
export function childIndicesOf(index) {
  return { left: 2 * index + 1, right: 2 * index + 2 };
}

/**
 * Splits a level-order array into the rows of the complete binary tree.
 *
 * Level 0 holds 1 node, level 1 holds up to 2, level 2 up to 4, and so on. The final level is
 * usually partly filled, which is exactly how a complete binary tree looks.
 */
export function buildTreeLevels(heapArray) {
  const levels = [];
  if (!Array.isArray(heapArray) || heapArray.length === 0) {
    return levels;
  }
  let index = 0;
  let width = 1;
  while (index < heapArray.length) {
    const level = [];
    for (let offset = 0; offset < width && index < heapArray.length; offset += 1, index += 1) {
      level.push({ index, applicant: heapArray[index] });
    }
    levels.push(level);
    width *= 2;
  }
  return levels;
}

/**
 * Client-side mirror of the Java validation rules.
 * Returns an object keyed by field name; an empty object means the form may be submitted.
 */
export function collectFieldErrors(values) {
  const errors = {};
  const name = (values.name ?? '').trim();
  const program = (values.program ?? '').trim();

  if (name === '') {
    errors.name = 'Applicant name is required.';
  } else if (name.length > 80) {
    errors.name = 'Applicant name must be 80 characters or fewer.';
  }

  if (program === '') {
    errors.program = 'Degree program is required.';
  } else if (program.length > 80) {
    errors.program = 'Degree program must be 80 characters or fewer.';
  }

  const gwa = Number(values.gwa);
  if (values.gwa === '' || values.gwa === null || values.gwa === undefined || Number.isNaN(gwa)) {
    errors.gwa = 'General weighted average is required.';
  } else if (gwa < 1 || gwa > 5) {
    errors.gwa = 'General weighted average must be between 1.00 and 5.00.';
  }

  const income = Number(values.monthlyIncome);
  if (values.monthlyIncome === '' || values.monthlyIncome === null || values.monthlyIncome === undefined || Number.isNaN(income)) {
    errors.monthlyIncome = 'Monthly household income is required.';
  } else if (income < 0 || income > 1000000) {
    errors.monthlyIncome = 'Monthly household income must be between 0 and 1,000,000.';
  }

  const units = Number(values.unitsEnrolled);
  if (values.unitsEnrolled === '' || values.unitsEnrolled === null || values.unitsEnrolled === undefined || Number.isNaN(units)) {
    errors.unitsEnrolled = 'Units enrolled is required.';
  } else if (!Number.isInteger(units)) {
    errors.unitsEnrolled = 'Units enrolled must be a whole number.';
  } else if (units < 1 || units > 36) {
    errors.unitsEnrolled = 'Units enrolled must be between 1 and 36.';
  }

  return errors;
}

/** One-line summary of the slot situation, shown when no operation message is more specific. */
export function describeQueue(report) {
  if (!report) {
    return 'The queue is empty.';
  }
  if (report.waitingCount === 0 && report.slotsRemaining === report.totalSlots) {
    return 'The queue is empty. Add an application or load the sample set.';
  }
  if (report.slotsRemaining === 0) {
    return `All ${report.totalSlots} slots are awarded. ${report.waitingCount} applicant(s) remain in the queue.`;
  }
  if (report.waitingCount === 0) {
    return `No applicants are waiting. ${report.slotsRemaining} of ${report.totalSlots} slots are still open.`;
  }
  const next = report.nextInLine;
  return `${report.waitingCount} waiting. ${next.name} holds the root with ${formatScore(next.meritScore)} points.`;
}

/** Turns the API error body into a single readable sentence. */
export function describeError(payload, fallback) {
  if (payload && Array.isArray(payload.errors) && payload.errors.length > 0) {
    return payload.errors.join(' ');
  }
  if (payload && typeof payload.message === 'string' && payload.message !== '') {
    return payload.message;
  }
  return fallback;
}
