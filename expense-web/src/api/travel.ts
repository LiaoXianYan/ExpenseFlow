import request from './request'

export function getTravelList(params: any) {
  return request.get('/expense/travel/page', { params })
}
export function getTravelDetail(id: number) {
  return request.get(`/expense/travel/${id}`)
}
export function createTravel(data: any) {
  return request.post('/expense/travel', data)
}
export function updateTravel(id: number, data: any) {
  return request.put(`/expense/travel/${id}`, data)
}
export function deleteTravel(id: number) {
  return request.delete(`/expense/travel/${id}`)
}
export function submitTravel(id: number) {
  return request.post(`/expense/travel/${id}/submit`)
}
export function withdrawTravel(id: number) {
  return request.post(`/expense/travel/${id}/withdraw`)
}
