/**
 * Thin wrapper over the JSON endpoints.
 *
 * `createApi` takes the fetch implementation as an argument so the Node test suite can pass a stub
 * and assert on the requests without a running server or a browser.
 */

/** Thrown for any non-2xx response; carries the parsed error body when there was one. */
export class ApiError extends Error {
  constructor(message, status, payload) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
  }
}

async function readBody(response) {
  const text = await response.text();
  if (text === '') {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export function createApi(fetchImpl = globalThis.fetch, baseUrl = '') {
  if (typeof fetchImpl !== 'function') {
    throw new TypeError('A fetch implementation is required.');
  }

  async function request(path, options = {}) {
    const response = await fetchImpl(`${baseUrl}${path}`, {
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
      ...options,
    });
    const payload = await readBody(response);
    if (!response.ok) {
      const message = payload && payload.message ? payload.message : `Request failed with status ${response.status}.`;
      throw new ApiError(message, response.status, payload);
    }
    return payload;
  }

  return {
    /** POST /api/applications - scores the application and inserts it into the heap. */
    submitApplication(application) {
      return request('/api/applications', { method: 'POST', body: JSON.stringify(application) });
    },

    /** POST /api/awards - removes the root of the heap and grants a slot. */
    awardSlot() {
      return request('/api/awards', { method: 'POST' });
    },

    /** GET /api/queue - reads the current queue without changing it. */
    fetchQueue() {
      return request('/api/queue');
    },

    /** POST /api/reset - clears the queue and the awarded slots. */
    resetQueue() {
      return request('/api/reset', { method: 'POST' });
    },

    /** POST /api/sample - replaces the queue with the demonstration set. */
    loadSample() {
      return request('/api/sample', { method: 'POST' });
    },
  };
}
