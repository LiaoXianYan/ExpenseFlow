import request from './request'

export function getDepartmentTree() {
  return request.get('/system/department/tree')
}
export function createDepartment(data: any) {
  return request.post('/system/department', data)
}
export function updateDepartment(id: number, data: any) {
  return request.put(`/system/department/${id}`, data)
}
export function deleteDepartment(id: number) {
  return request.delete(`/system/department/${id}`)
}
export function getEmployeePage(params: any) {
  return request.get('/system/employee/page', { params })
}
