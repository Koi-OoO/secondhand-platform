import test from 'node:test'
import assert from 'node:assert/strict'
import { orderTimeline, orderActions } from './orderDetail.js'
test('cancelled orders show missing historical cancellation time honestly', () => {
 const events = orderTimeline({ status: 4, createTime: '2026-10-01', cancelType: 2 })
 assert.deepEqual(events.map(e=>e.label), ['下单', '卖家拒绝交付'])
 assert.equal(events[1].time, null)
})
test('completed orders show actual stages and evaluation time', () => {
 assert.deepEqual(orderTimeline({ status: 3, deliverTime: 'd', finishTime: 'f', evaluation: {createTime: 'e'} }).map(e=>e.label), ['下单', '卖家声明已交付', '买家确认完成', '买家评价'])
})
test('operations depend on role, state and existing evaluation', () => {
 assert.deepEqual(orderActions({buyerId: 2, sellerId: 3, status:1}, 2), ['cancel'])
 assert.deepEqual(orderActions({buyerId: 2, sellerId: 3, status:1,meetupTime:'2099-01-01 18:00:00'}, 3), ['deliver', 'reject'])
 assert.deepEqual(orderActions({buyerId: 2, sellerId: 3, status:2}, 2), ['confirm', 'issue'])
 assert.deepEqual(orderActions({buyerId: 2, sellerId: 3, status:3, evaluated:true}, 2), [])
 assert.deepEqual(orderActions({buyerId: 2, sellerId: 3, status:3}, 2), ['evaluate'])
 assert.deepEqual(orderActions({buyerId: 2, sellerId: 3, status:1}, 99), [])
})

test('seller must accept before meetup and acceptance appears on timeline', () => {
 assert.deepEqual(orderActions({buyerId:2,sellerId:3,status:5},3), ['accept','reject'])
 assert.deepEqual(orderActions({buyerId:2,sellerId:3,status:5},2), ['cancel'])
 assert.deepEqual(orderTimeline({status:1,createTime:'c',acceptTime:'a'}).map(e=>e.label), ['下单','卖家接单'])
})

test('buyer can report a delivery issue, seller can only remind while waiting',()=>{
 assert.deepEqual(orderActions({buyerId:2,sellerId:3,status:2},2),['confirm','issue'])
 assert.deepEqual(orderActions({buyerId:2,sellerId:3,status:2},3),['remind'])
 assert.deepEqual(orderActions({buyerId:2,sellerId:3,status:6},3),[])
 assert.deepEqual(orderActions({buyerId:2,sellerId:3,status:6},2),['confirm'])
})

test('seller cannot declare delivery before buyer confirms the initial meetup time',()=>{
 assert.deepEqual(orderActions({buyerId:2,sellerId:3,status:1,meetupTime:null},3),['reject'])
 assert.deepEqual(orderActions({buyerId:2,sellerId:3,status:1,meetupTime:'2099-01-01 18:00:00'},3),['deliver','reject'])
})
