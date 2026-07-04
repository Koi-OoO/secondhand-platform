import test from 'node:test'
import assert from 'node:assert/strict'
import { relativeTime } from './format.js'

test('relativeTime displays future timestamps as absolute time', () => {
  const originalNow = Date.now
  const now = new Date(2026, 6, 4, 20, 0, 0)
  const future = new Date(2026, 6, 4, 21, 0, 0)
  Date.now = () => now.getTime()

  try {
    assert.equal(relativeTime(future), '2026-07-04 21:00')
  } finally {
    Date.now = originalNow
  }
})
