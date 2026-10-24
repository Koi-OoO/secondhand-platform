import test from 'node:test'
import assert from 'node:assert/strict'
import { canRespondToProposal } from './meetupProposal.js'
test('only buyer can respond to future pending proposal on meetup order',()=>{
 const p={buyerId:2,orderStatus:1,effectiveStatus:'pending',proposedTime:'2099-10-24 18:00:00'}
 assert.equal(canRespondToProposal(p,2),true)
 assert.equal(canRespondToProposal({...p,proposedTime:new Date('2099-10-24T18:00:00').getTime()},2),true)
 assert.equal(canRespondToProposal(p,3),false)
 for(const status of ['confirmed','declined','expired']) assert.equal(canRespondToProposal({...p,effectiveStatus:status},2),false)
 assert.equal(canRespondToProposal({...p,orderStatus:4},2),false)
 assert.equal(canRespondToProposal({...p,proposedTime:'2000-01-01 00:00:00'},2),false)
})
