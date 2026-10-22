import test from 'node:test'
import assert from 'node:assert/strict'
import { getRequestUserId, setRequestUserId, bindSocketIdentity } from './requestIdentity.js'

test('keeps the current tab identity in memory and clears it on logout', () => {
  setRequestUserId(7)
  assert.equal(getRequestUserId(), '7')
  setRequestUserId(null)
  assert.equal(getRequestUserId(), null)
  setRequestUserId('invalid')
  assert.equal(getRequestUserId(), null)
})

test('a reconnect binds the visible identity without losing existing socket URL parameters', () => {
  setRequestUserId(7)
  const url = new URL(bindSocketIdentity('ws://localhost:8080/ws?client=web'))
  assert.equal(url.searchParams.get('activeUserId'), '7')
  assert.equal(url.searchParams.get('client'), 'web')
  setRequestUserId(null)
})
