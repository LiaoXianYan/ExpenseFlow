import request from './request'

export function getBudgetPage(params: any) {
  return request.get('/expense/budget/page', { params })
}
export function getBudgetDetail(id: number) {
  return request.get(`/expense/budget/${id}`)
}
export function createBudget(data: any) {
  return request.post('/expense/budget', data)
}
export function updateBudget(id: number, data: any) {
  return request.put(`/expense/budget/${id}`, data)
}
export function deleteBudget(id: number) {
  return request.delete(`/expense/budget/${id}`)
}
