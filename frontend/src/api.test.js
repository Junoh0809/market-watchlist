import assert from 'node:assert/strict';
import test from 'node:test';
import { createApiClient } from './api.js';

test('login sends credentials without an Authorization header', async () => {
  let sent;
  const api = createApiClient('/api/v1', async (url, options) => {
    sent = { url, options };
    return new Response(JSON.stringify({ accessToken: 'session' }), { status: 200 });
  });
  const result = await api.request('/auth/login', { method: 'POST', body: { email: 'a@example.com', password: 'pass' } });
  assert.equal(sent.url, '/api/v1/auth/login');
  assert.equal(sent.options.headers.Authorization, undefined);
  assert.deepEqual(JSON.parse(sent.options.body), { email: 'a@example.com', password: 'pass' });
  assert.equal(result.accessToken, 'session');
});

test('successful empty responses resolve without JSON parsing', async () => {
  const api = createApiClient('/api/v1', async () => new Response(null, { status: 200 }));
  assert.equal(await api.request('/auth/logout', { method: 'POST', token: 'session' }), null);
});

test('API error message is shown without exposing a raw JSON response', async () => {
  const api = createApiClient('/api/v1', async () => new Response(JSON.stringify({ error: { message: '이미 등록된 종목입니다.' } }), { status: 409, headers: { 'Content-Type': 'application/json' } }));
  await assert.rejects(api.request('/me/watchlist', { method: 'POST', token: 'session' }), { message: '이미 등록된 종목입니다.' });
});

test('expired sessions remain distinguishable from other errors', async () => {
  const api = createApiClient('/api/v1', async () => new Response(null, { status: 401 }));
  await assert.rejects(api.request('/me/watchlist', { token: 'session' }), { unauthorized: true });
});
