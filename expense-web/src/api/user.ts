import request from './request'

export function getUserPage(params: any) {
  return request.get('/system/user/page', { params })
}
export function getUserDetail(id: number) {
  return request.get(`/system/user/${id}`)
}
export function createUser(data: any) {
  return request.post('/system/user', data)
}
export function updateUser(id: number, data: any) {
  return request.put(`/system/user/${id}`, data)
}
export function deleteUser(id: number) {
  return request.delete(`/system/user/${id}`)
}
export function updateUserStatus(id: number, status: string) {
  return request.put(`/system/user/${id}/status`, { status })
}
export function resetPassword(id: number) {
  return request.put(`/system/user/${id}/reset-password`)
}
export function getUserRoles(id: number) {
  return request.get(`/system/user/${id}/roles`)
}
