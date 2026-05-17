import request from './request'

export function getMyPermissions() {
  return request.get<string[]>('/system/permission/my')
}
