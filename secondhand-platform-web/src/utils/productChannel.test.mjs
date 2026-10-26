import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createProductChannel, createRefreshQueue } from './productChannel.js'

class Socket {
  static list = []
  constructor() { this.sent=[]; this.readyState=0; Socket.list.push(this) }
  send(text) { this.sent.push(JSON.parse(text === 'ping' ? '{}' : text)) }
  close() { this.readyState=3; this.onclose?.() }
  open() { this.readyState=1; this.onopen?.() }
  receive(data) { this.onmessage?.({ data: JSON.stringify(data) }) }
}
test('public channel replaces subscriptions, ignores unrelated events and stale sockets, and reconnects', () => {
  Socket.list=[]
  let ready=0, changed=[], retry
  const channel=createProductChannel({ url:'ws://test/ws/products', Socket,
    onReady:()=>ready++, onChange:id=>changed.push(id),
    later:fn=>{retry=fn;return 1}, cancel:()=>{}, every:()=>1, cancelEvery:()=>{} })
  channel.start([40,40]); const first=Socket.list[0]; first.open()
  assert.deepEqual(first.sent[0],{type:'subscribe',productIds:[40]})
  first.receive({type:'subscribed'}); assert.equal(ready,1)
  first.receive({type:'product_changed',productId:41}); assert.deepEqual(changed,[])
  first.receive({type:'product_changed',productId:40}); assert.deepEqual(changed,[40])
  channel.start([41]); assert.deepEqual(first.sent.at(-1).productIds,[41])
  first.close(); retry(); const second=Socket.list[1]; second.open()
  assert.deepEqual(second.sent[0].productIds,[41])
  first.receive({type:'product_changed',productId:41}); assert.deepEqual(changed,[40])
  second.receive({type:'subscribed'}); assert.equal(ready,2)
  channel.stop(); second.receive({type:'product_changed',productId:41}); assert.deepEqual(changed,[40])
})
test('refresh queue coalesces bursts and reruns when an event arrives during a request', async () => {
  let callback, release, calls=0
  const queue=createRefreshQueue(async()=>{calls++; await new Promise(resolve=>{release=resolve})}, {
    later:fn=>{callback=fn;return 1},cancel:()=>{} })
  queue.request(); queue.request(); const first=callback(); assert.equal(calls,1)
  queue.request(); release(); await first
  const second=callback(); assert.equal(calls,2); release(); await second
  queue.stop(); queue.request(); assert.equal(calls,2)
})

test('missing pong closes half-open connection, while received pong keeps it alive', () => {
  Socket.list=[]; let beat
  const channel=createProductChannel({url:'ws://test',Socket,onReady:()=>{},onChange:()=>{},
    later:()=>1,cancel:()=>{},every:fn=>{beat=fn;return 1},cancelEvery:()=>{}})
  channel.start([40]); const socket=Socket.list[0];socket.open()
  beat(); socket.onmessage({data:'pong'}); beat()
  assert.equal(socket.readyState,1)
  beat(); assert.equal(socket.readyState,3)
  channel.stop()
})

test('stalled handshake times out and a stopped client cannot reconnect', () => {
  Socket.list=[]; let timeout
  const channel=createProductChannel({url:'ws://test',Socket,onReady:()=>{},onChange:()=>{},
    later:fn=>{timeout=fn;return 1},cancel:()=>{},every:()=>1,cancelEvery:()=>{}})
  channel.start([40]); const socket=Socket.list[0]
  assert.equal(typeof timeout,'function'); timeout()
  assert.equal(socket.readyState,3)
  channel.stop(); timeout(); assert.equal(Socket.list.length,1)
})
