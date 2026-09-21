import assert from 'node:assert/strict';
import test from 'node:test';

import { ApiError, createApi } from '../../main/resources/static/js/api.js';

/** Builds a fetch stub that records calls and replies with a fixed status and body. */
function stubFetch({ status = 200, body = {} } = {}) {
  const calls = [];
  const fetchImpl = async (url, options = {}) => {
    calls.push({ url, options });
    return {
      ok: status >= 200 && status < 300,
      status,
      text: async () => (body === null ? '' : JSON.stringify(body)),
    };
  };
  return { fetchImpl, calls };
}

test('createApi requires a fetch implementation', () => {
  assert.throws(() => createApi(null), TypeError);
  assert.throws(() => createApi('nope'), TypeError);
});

test('submitApplication posts JSON to /api/applications', async () => {
  const { fetchImpl, calls } = stubFetch({ status: 201, body: { message: 'ok' } });
  const api = createApi(fetchImpl);

  const application = { name: 'Maria', program: 'BSIT', gwa: 1.5, monthlyIncome: 12000, unitsEnrolled: 24 };
  const result = await api.submitApplication(application);

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/applications');
  assert.equal(calls[0].options.method, 'POST');
  assert.equal(calls[0].options.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(calls[0].options.body), application);
  assert.deepEqual(result, { message: 'ok' });
});

test('fetchQueue issues a GET without a body', async () => {
  const { fetchImpl, calls } = stubFetch({ body: { waitingCount: 0 } });

  await createApi(fetchImpl).fetchQueue();

  assert.equal(calls[0].url, '/api/queue');
  assert.equal(calls[0].options.method, undefined);
  assert.equal(calls[0].options.body, undefined);
  assert.equal(calls[0].options.headers['Content-Type'], undefined);
});

test('awardSlot, resetQueue, and loadSample post to their endpoints', async () => {
  const { fetchImpl, calls } = stubFetch({ body: {} });
  const api = createApi(fetchImpl);

  await api.awardSlot();
  await api.resetQueue();
  await api.loadSample();

  assert.deepEqual(calls.map((call) => call.url), ['/api/awards', '/api/reset', '/api/sample']);
  assert.ok(calls.every((call) => call.options.method === 'POST'));
});

test('a base URL is prefixed to every path', async () => {
  const { fetchImpl, calls } = stubFetch({ body: {} });

  await createApi(fetchImpl, 'http://localhost:9999').fetchQueue();

  assert.equal(calls[0].url, 'http://localhost:9999/api/queue');
});

test('a failed request raises ApiError carrying the status and payload', async () => {
  const payload = { status: 400, message: 'The submitted application is not valid.', errors: ['Applicant name is required.'] };
  const { fetchImpl } = stubFetch({ status: 400, body: payload });

  await assert.rejects(
    () => createApi(fetchImpl).submitApplication({}),
    (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 400);
      assert.equal(error.message, 'The submitted application is not valid.');
      assert.deepEqual(error.payload, payload);
      return true;
    },
  );
});

test('a failure with no readable body still raises ApiError with the status', async () => {
  const { fetchImpl } = stubFetch({ status: 409, body: null });

  await assert.rejects(
    () => createApi(fetchImpl).awardSlot(),
    (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 409);
      assert.match(error.message, /status 409/);
      assert.equal(error.payload, null);
      return true;
    },
  );
});

test('an unreadable success body resolves to null rather than throwing', async () => {
  const fetchImpl = async () => ({ ok: true, status: 200, text: async () => 'not json at all' });

  assert.equal(await createApi(fetchImpl).fetchQueue(), null);
});

test('a network failure propagates unchanged', async () => {
  const fetchImpl = async () => {
    throw new Error('connection refused');
  };

  await assert.rejects(() => createApi(fetchImpl).fetchQueue(), /connection refused/);
});
