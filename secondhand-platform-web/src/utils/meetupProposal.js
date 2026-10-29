import { ORDER_WAIT_MEETUP } from './constant.js'

export function proposalTime(value) { return new Date(typeof value === 'string' ? value.replace(' ', 'T') : value).getTime() }

export function canRespondToProposal(proposal, userId, now = Date.now()) {
  return !!proposal && Number(proposal.buyerId) === Number(userId)
    && proposal.orderStatus === ORDER_WAIT_MEETUP && proposal.effectiveStatus === 'pending'
    && proposalTime(proposal.proposedTime) > now
}
