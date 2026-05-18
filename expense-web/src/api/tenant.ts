import request from './request'

export function getTenantPage(params: any) {
  return request.get('/system/tenant/page', { params })
}
export function getTenantDetail(id: number) {
  return request.get(`/system/tenant/${id}`)
}
export function createTenant(data: any) {
  return request.post('/system/tenant', data)
}
export function updateTenant(id: number, data: any) {
  return request.put(`/system/tenant/${id}`, data)
}
export function deleteTenant(id: number) {
  return request.delete(`/system/tenant/${id}`)
}
export function updateTenantStatus(id: number, status: string) {
  return request.put(`/system/tenant/${id}/status`, { status })
}
