import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildTreeLevels,
  childIndicesOf,
  collectFieldErrors,
  describeError,
  describeQueue,
  formatGwa,
  formatPeso,
  formatScore,
  parentIndexOf,
} from '../../main/resources/static/js/heap-view.js';

const validForm = {
  name: 'Maria Santos',
  program: 'BS Information Technology',
  gwa: '1.50',
  monthlyIncome: '12000',
  unitsEnrolled: '24',
};

test('formatScore always shows two decimals', () => {
  assert.equal(formatScore(72), '72.00');
  assert.equal(formatScore(72.5), '72.50');
  assert.equal(formatScore(72.456), '72.46');
  assert.equal(formatGwa(1.5), '1.50');
});

test('formatScore and formatPeso fall back to a dash for missing values', () => {
  assert.equal(formatScore(null), '—');
  assert.equal(formatScore(undefined), '—');
  assert.equal(formatScore('not a number'), '—');
  assert.equal(formatPeso(null), '—');
});

test('formatPeso groups thousands and drops decimals', () => {
  assert.match(formatPeso(12000), /^₱12[, \s]?000$/);
  assert.match(formatPeso(0), /^₱0$/);
});

test('parentIndexOf mirrors the Java index arithmetic', () => {
  assert.equal(parentIndexOf(0), null);
  assert.equal(parentIndexOf(1), 0);
  assert.equal(parentIndexOf(2), 0);
  assert.equal(parentIndexOf(3), 1);
  assert.equal(parentIndexOf(4), 1);
  assert.equal(parentIndexOf(5), 2);
  assert.equal(parentIndexOf(6), 2);
});

test('parentIndexOf rejects values that are not a real index', () => {
  assert.equal(parentIndexOf(-3), null);
  assert.equal(parentIndexOf(1.5), null);
  assert.equal(parentIndexOf('2'), null);
});

test('childIndicesOf mirrors the Java index arithmetic', () => {
  assert.deepEqual(childIndicesOf(0), { left: 1, right: 2 });
  assert.deepEqual(childIndicesOf(3), { left: 7, right: 8 });
});

test('parent and child index arithmetic are inverses', () => {
  for (let index = 1; index < 64; index += 1) {
    const parent = parentIndexOf(index);
    const children = childIndicesOf(parent);
    assert.ok(children.left === index || children.right === index);
  }
});

test('buildTreeLevels splits a level-order array into complete-tree rows', () => {
  const heapArray = [0, 1, 2, 3, 4, 5, 6, 7].map((n) => ({ name: `A${n}`, meritScore: 100 - n }));
  const levels = buildTreeLevels(heapArray);

  assert.equal(levels.length, 4);
  assert.deepEqual(levels.map((level) => level.length), [1, 2, 4, 1]);
  assert.equal(levels[0][0].index, 0);
  assert.equal(levels[1][1].index, 2);
  assert.equal(levels[3][0].index, 7);
  assert.equal(levels[3][0].applicant.name, 'A7');
});

test('buildTreeLevels returns no rows for an empty or missing array', () => {
  assert.deepEqual(buildTreeLevels([]), []);
  assert.deepEqual(buildTreeLevels(null), []);
  assert.deepEqual(buildTreeLevels(undefined), []);
});

test('buildTreeLevels covers every element exactly once', () => {
  for (const size of [1, 2, 3, 5, 9, 16, 31]) {
    const heapArray = Array.from({ length: size }, (_, n) => ({ name: `A${n}` }));
    const flattened = buildTreeLevels(heapArray).flat().map((node) => node.index);
    assert.deepEqual(flattened, Array.from({ length: size }, (_, n) => n));
  }
});

test('collectFieldErrors accepts a complete, in-range form', () => {
  assert.deepEqual(collectFieldErrors(validForm), {});
});

test('collectFieldErrors flags every missing field', () => {
  const errors = collectFieldErrors({ name: '  ', program: '', gwa: '', monthlyIncome: '', unitsEnrolled: '' });
  assert.deepEqual(Object.keys(errors).sort(), ['gwa', 'monthlyIncome', 'name', 'program', 'unitsEnrolled']);
});

test('collectFieldErrors rejects an out-of-range general weighted average', () => {
  assert.ok(collectFieldErrors({ ...validForm, gwa: '0.9' }).gwa);
  assert.ok(collectFieldErrors({ ...validForm, gwa: '5.1' }).gwa);
  assert.equal(collectFieldErrors({ ...validForm, gwa: '1' }).gwa, undefined);
  assert.equal(collectFieldErrors({ ...validForm, gwa: '5' }).gwa, undefined);
});

test('collectFieldErrors rejects an out-of-range income', () => {
  assert.ok(collectFieldErrors({ ...validForm, monthlyIncome: '-1' }).monthlyIncome);
  assert.ok(collectFieldErrors({ ...validForm, monthlyIncome: '1000001' }).monthlyIncome);
  assert.equal(collectFieldErrors({ ...validForm, monthlyIncome: '0' }).monthlyIncome, undefined);
});

test('collectFieldErrors rejects a non-integer or out-of-range unit load', () => {
  assert.ok(collectFieldErrors({ ...validForm, unitsEnrolled: '20.5' }).unitsEnrolled);
  assert.ok(collectFieldErrors({ ...validForm, unitsEnrolled: '0' }).unitsEnrolled);
  assert.ok(collectFieldErrors({ ...validForm, unitsEnrolled: '37' }).unitsEnrolled);
  assert.equal(collectFieldErrors({ ...validForm, unitsEnrolled: '36' }).unitsEnrolled, undefined);
});

test('collectFieldErrors rejects over-long text fields', () => {
  assert.ok(collectFieldErrors({ ...validForm, name: 'x'.repeat(81) }).name);
  assert.ok(collectFieldErrors({ ...validForm, program: 'x'.repeat(81) }).program);
});

test('describeQueue reports the empty state', () => {
  const report = { waitingCount: 0, slotsRemaining: 5, totalSlots: 5, nextInLine: null };
  assert.match(describeQueue(report), /queue is empty/i);
  assert.match(describeQueue(null), /empty/i);
});

test('describeQueue names the applicant holding the root', () => {
  const report = {
    waitingCount: 3,
    slotsRemaining: 2,
    totalSlots: 5,
    nextInLine: { name: 'Bea Navarro', meritScore: 96.25 },
  };
  const text = describeQueue(report);
  assert.match(text, /Bea Navarro/);
  assert.match(text, /96\.25/);
  assert.match(text, /root/);
});

test('describeQueue reports exhausted slots and a drained queue', () => {
  assert.match(
    describeQueue({ waitingCount: 4, slotsRemaining: 0, totalSlots: 5, nextInLine: { name: 'X', meritScore: 1 } }),
    /All 5 slots are awarded/,
  );
  assert.match(
    describeQueue({ waitingCount: 0, slotsRemaining: 3, totalSlots: 5, nextInLine: null }),
    /No applicants are waiting/,
  );
});

test('describeError prefers the field errors, then the message, then the fallback', () => {
  assert.equal(describeError({ errors: ['One.', 'Two.'], message: 'Ignored' }, 'fallback'), 'One. Two.');
  assert.equal(describeError({ errors: [], message: 'Only message.' }, 'fallback'), 'Only message.');
  assert.equal(describeError(null, 'fallback'), 'fallback');
  assert.equal(describeError({}, 'fallback'), 'fallback');
});
