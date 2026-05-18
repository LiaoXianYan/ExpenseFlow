import request from './request'

export function getPolicyPage(params: any) {
  return request.get('/expense/policy/page', { params })
}
export function getPolicyDetail(id: number) {
  return request.get(`/expense/policy/${id}`)
}
export function createPolicy(data: any) {
  return request.post('/expense/policy', data)
}
export function updatePolicy(id: number, data: any) {
  return request.put(`/expense/policy/${id}`, data)
}
export function deletePolicy(id: number) {
  return request.delete(`/expense/policy/${id}`)
}
