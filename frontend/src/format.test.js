import assert from 'node:assert/strict';
import test from 'node:test';
import { formatKoreanTime, formatNumber, formatChange, statusText } from './format.js';

test('market timestamps display in Asia/Seoul across US daylight saving changes', () => {
  assert.match(formatKoreanTime('2026-03-08T06:30:00Z'), /2026.*03.*08.*15:30/);
  assert.match(formatKoreanTime('2026-03-08T07:30:00Z'), /2026.*03.*08.*16:30/);
});

test('negative change rates retain their sign', () => {
  assert.equal(formatChange(-1.25, '%'), '-1.25%');
  assert.equal(formatChange(1.25, '%'), '+1.25%');
  assert.equal(formatChange(null, '%'), '—');
});

test('missing prices and delayed statuses are explicit', () => {
  assert.equal(formatNumber(null, 'USD'), '—');
  assert.equal(statusText('LAST_KNOWN'), '업데이트 중 · 마지막 정상 데이터');
  assert.equal(statusText('UPDATING'), '업데이트 중');
});
