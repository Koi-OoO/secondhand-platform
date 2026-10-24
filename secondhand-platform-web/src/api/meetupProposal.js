import http from './index'
export function proposeMeetup(orderId, proposedTime) { return http.post('/meetup-proposals', { orderId, proposedTime }) }
export function respondToMeetup(id, accept) { return http.put(`/meetup-proposals/${id}/response`, { accept }) }
export function getMeetupProposal(id) { return http.get(`/meetup-proposals/${id}`) }
