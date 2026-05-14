import request from './request'

export function getReportList(params: any) {
  return request.get('/expense/report/page', { params })
}
export function getReportDetail(id: number) {
  return request.get(`/expense/report/${id}`)
}
export function createReport(data: any) {
  return request.post('/expense/report', data)
}
export function updateReport(id: number, data: any) {
  return request.put(`/expense/report/${id}`, data)
}
export function deleteReport(id: number) {
  return request.delete(`/expense/report/${id}`)
}
export function submitReport(id: number) {
  return request.post(`/expense/report/${id}/submit`)
}
export function addReportItem(reportId: number, data: any) {
  return request.post(`/expense/report/${reportId}/item`, data)
}
export function deleteReportItem(reportId: number, itemId: number) {
  return request.delete(`/expense/report/${reportId}/item/${itemId}`)
}
export function payReport(reportId: number) {
  return request.post(`/expense/payment/pay?reportId=${reportId}`)
}
