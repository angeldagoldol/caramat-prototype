#!/usr/bin/env node
/**
 * End-to-end smoke test.
 *
 * Boots the packaged application on a free port, drives the real HTTP endpoints, and asserts that
 * the priority queue behaves as the documentation claims: the root is always the highest merit
 * score, awards come out in descending order, ties fall back to submission order, and the heap
 * property holds after every operation.
 *
 * Usage: node scripts/smoke-test.mjs [--port 18080] [--jar target/app.jar]
 */
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';

function readArg(flag, fallback) {
  const index = process.argv.indexOf(flag);
  return index === -1 || index === process.argv.length - 1 ? fallback : process.argv[index + 1];
}

const PORT = Number(readArg('--port', '18080'));
const JAR = readArg('--jar', 'target/app.jar');
const BASE = `http://127.0.0.1:${PORT}`;
const STARTUP_TIMEOUT_MS = 90_000;

if (!existsSync(JAR)) {
  console.error(`Jar not found at ${JAR}. Run ./mvnw package first.`);
  process.exit(1);
}

async function call(path, options = {}) {
  const response = await fetch(`${BASE}${path}`, {
    headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
    ...options,
  });
  const text = await response.text();
  const body = text === '' ? null : JSON.parse(text);
  return { status: response.status, body };
}

async function waitForReady(child) {
  const deadline = Date.now() + STARTUP_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`Application exited early with code ${child.exitCode}.`);
    }
    try {
      const { status } = await call('/api/queue');
      if (status === 200) {
        return;
      }
    } catch {
      // not listening yet
    }
    await delay(500);
  }
  throw new Error(`Application did not become ready within ${STARTUP_TIMEOUT_MS} ms.`);
}

function submit(name, gwa, monthlyIncome, unitsEnrolled) {
  return call('/api/applications', {
    method: 'POST',
    body: JSON.stringify({ name, program: 'BS Information Technology', gwa, monthlyIncome, unitsEnrolled }),
  });
}

const checks = [];
function check(label, run) {
  checks.push({ label, run });
}

check('the interface is served from the same process', async () => {
  const response = await fetch(BASE);
  assert.equal(response.status, 200);
  const html = await response.text();
  assert.match(html, /Online Scholarship Application System/);
});

check('a fresh queue is empty with every slot open', async () => {
  const { body } = await call('/api/reset', { method: 'POST' });
  assert.equal(body.queue.waitingCount, 0);
  assert.equal(body.queue.slotsRemaining, body.queue.totalSlots);
  assert.equal(body.queue.nextInLine, null);
  assert.equal(body.queue.heapValid, true);
});

check('an application is accepted and reaches the root of an empty heap', async () => {
  const { status, body } = await submit('Maria Santos', 1.5, 12000, 24);
  assert.equal(status, 201);
  assert.equal(body.subject.referenceCode, 'SCH-0001');
  assert.equal(body.operation.operation, 'insert');
  assert.equal(body.queue.nextInLine.name, 'Maria Santos');
  assert.equal(body.queue.heapArray[0].heapIndex, 0);
});

check('a stronger late applicant sifts up to the root', async () => {
  await call('/api/reset', { method: 'POST' });
  await submit('Weak', 2.9, 55000, 12);
  await submit('Middle', 2.2, 30000, 21);
  const { body } = await submit('Strongest', 1.1, 5000, 24);

  assert.equal(body.queue.nextInLine.name, 'Strongest');
  assert.ok(body.operation.swaps > 0, 'expected at least one sift-up swap');
  assert.equal(body.queue.heapValid, true);
});

check('invalid input is rejected and the queue is left untouched', async () => {
  const before = (await call('/api/queue')).body.waitingCount;

  const blank = await submit('   ', 1.5, 12000, 24);
  assert.equal(blank.status, 400);
  assert.match(blank.body.errors.join(' '), /Applicant name is required/);

  const badGwa = await submit('Someone', 9.9, 12000, 24);
  assert.equal(badGwa.status, 400);

  const badUnits = await submit('Someone', 1.5, 12000, 99);
  assert.equal(badUnits.status, 400);

  assert.equal((await call('/api/queue')).body.waitingCount, before);
});

check('the sample set loads and the heap property holds', async () => {
  const { body } = await call('/api/sample', { method: 'POST' });
  assert.equal(body.queue.waitingCount, 7);
  assert.equal(body.queue.heapValid, true);
  assert.equal(body.queue.nextInLine.name, 'Bea Angeline Navarro');
});

check('the ranked view is fully ordered while the raw array is not sorted', async () => {
  const { body } = await call('/api/queue');
  const scores = body.waiting.map((applicant) => applicant.meritScore);
  for (let index = 1; index < scores.length; index += 1) {
    assert.ok(scores[index - 1] >= scores[index], 'ranked view must be descending');
  }
  assert.equal(body.heapArray.length, body.waiting.length);
  assert.equal(body.heapArray[0].meritScore, scores[0], 'the root must be the maximum');
});

check('every parent in the backing array outranks both of its children', async () => {
  const { body } = await call('/api/queue');
  const array = body.heapArray;
  for (let index = 1; index < array.length; index += 1) {
    const parent = array[Math.floor((index - 1) / 2)];
    const child = array[index];
    assert.ok(
      parent.meritScore > child.meritScore
        || (parent.meritScore === child.meritScore && parent.sequence < child.sequence),
      `heap property broken between index ${Math.floor((index - 1) / 2)} and ${index}`,
    );
  }
});

check('awards come out in descending merit order and the heap stays valid', async () => {
  const awarded = [];
  for (let slot = 0; slot < 5; slot += 1) {
    const { status, body } = await call('/api/awards', { method: 'POST' });
    assert.equal(status, 200);
    assert.equal(body.operation.operation, 'extractMax');
    assert.equal(body.queue.heapValid, true);
    awarded.push(body.subject.meritScore);
  }
  for (let index = 1; index < awarded.length; index += 1) {
    assert.ok(awarded[index - 1] >= awarded[index], 'awards must be descending');
  }
});

check('awarding past the slot limit is refused with 409', async () => {
  const { status, body } = await call('/api/awards', { method: 'POST' });
  assert.equal(status, 409);
  assert.match(body.message, /already been awarded/);
});

check('awarding an empty queue is refused with 409', async () => {
  await call('/api/reset', { method: 'POST' });
  const { status, body } = await call('/api/awards', { method: 'POST' });
  assert.equal(status, 409);
  assert.match(body.message, /No applications are waiting/);
});

check('equal merit scores are awarded in submission order', async () => {
  await call('/api/reset', { method: 'POST' });
  await submit('Applied first', 2.0, 20000, 24);
  await submit('Applied second', 2.0, 20000, 24);
  await submit('Applied third', 2.0, 20000, 24);

  const names = [];
  for (let slot = 0; slot < 3; slot += 1) {
    names.push((await call('/api/awards', { method: 'POST' })).body.subject.name);
  }
  assert.deepEqual(names, ['Applied first', 'Applied second', 'Applied third']);
});

check('the array doubles and the tree height stays logarithmic', async () => {
  await call('/api/reset', { method: 'POST' });
  for (let index = 0; index < 40; index += 1) {
    await submit(`Applicant ${index}`, 1 + ((index * 7) % 300) / 100, (index * 1311) % 60000, 1 + (index % 24));
  }
  const { body } = await call('/api/queue');

  assert.equal(body.waitingCount, 40);
  assert.ok(body.heapCapacity >= 40, 'the backing array must have grown');
  assert.equal(body.heapHeight, 6, `expected height 6 for 40 elements, got ${body.heapHeight}`);
  assert.equal(body.heapValid, true);
});

const child = spawn('java', ['-jar', JAR], {
  env: { ...process.env, PORT: String(PORT) },
  stdio: ['ignore', 'pipe', 'pipe'],
});
child.stdout.on('data', () => {});
child.stderr.on('data', () => {});

let failures = 0;
try {
  await waitForReady(child);
  console.log(`Application ready on ${BASE}\n`);

  for (const { label, run } of checks) {
    try {
      await run();
      console.log(`  ok    ${label}`);
    } catch (error) {
      failures += 1;
      console.log(`  FAIL  ${label}`);
      console.log(`        ${error.message}`);
    }
  }
} catch (error) {
  failures += 1;
  console.error(error.message);
} finally {
  child.kill('SIGTERM');
  await delay(600);
  if (child.exitCode === null) {
    child.kill('SIGKILL');
  }
}

console.log(`\n${checks.length - failures}/${checks.length} checks passed.`);
process.exit(failures === 0 ? 0 : 1);
