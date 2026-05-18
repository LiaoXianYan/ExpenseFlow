import request from './request'

export function getRolePage(params: any) {
  return request.get('/system/role/page', { params })
}
export function getRoleDetail(id: number) {
  return request.get(`/system/role/${id}`)
}
export function createRole(data: any) {
  return request.post('/system/role', data)
}
export function updateRole(id: number, data: any) {
  return request.put(`/system/role/${id}`, data)
}
export function deleteRole(id: number) {
  return request.delete(`/system/role/${id}`)
}
export function assignRoleUsers(roleId: number, userIds: number[]) {
  return request.post(`/system/role/${roleId}/users`, { userIds })
}
export function assignRolePermissions(roleId: number, permissionIds: number[]) {
  return request.post(`/system/role/${roleId}/permissions`, { permissionIds })
}
export function getRolePermissions(roleId: number) {
  return request.get(`/system/role/${roleId}/permissions`)
}
export function getAllPermissions() {
  return request.get('/system/permission/all')
}
