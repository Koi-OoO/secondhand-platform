import test from 'node:test'
import assert from 'node:assert/strict'
import { totalUnread } from './notification.js'
test('header combines independent unread counts', () => {
  assert.equal(totalUnread(3, 4), 7)
  assert.equal(totalUnread('2', null), 2)
})
