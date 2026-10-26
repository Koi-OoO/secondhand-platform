import test from 'node:test'
import assert from 'node:assert/strict'
import { totalUnread, notificationActor } from './notification.js'
test('header combines independent unread counts', () => {
  assert.equal(totalUnread(3, 4), 7)
  assert.equal(totalUnread('2', null), 2)
})

test('favorite actor links only to a valid internal public profile', () => {
  assert.deepEqual(notificationActor({ type: 'favorite', actorUserId: 7, actorNickname: '小明', actorAvatar: '/a.png' }), {
    name: '小明', avatar: '/a.png', route: { name: 'SellerProfile', params: { id: '7' } }
  })
  assert.equal(notificationActor({ type: 'favorite' }), null)
  assert.equal(notificationActor({ type: 'favorite', actorUserId: '../admin' }), null)
  assert.equal(notificationActor({ type: 'order', actorUserId: 7 }), null)
  assert.equal(notificationActor({ type: 'favorite', actorUserId: 0 }), null)
  assert.equal(notificationActor({ type: 'favorite', actorUserId: 7 }).name, '校园用户')
})
