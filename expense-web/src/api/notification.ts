import request from './request'

export function getMessageList(params: any) {
  return request.get('/notification/message/page', { params })
}
export function markRead(messageId: number) {
  return request.put(`/notification/message/${messageId}/read`)
}
export function markAllRead() {
  return request.put('/notification/message/read-all')
}
export function getUnreadCount() {
  return request.get('/notification/message/unread-count')
}
