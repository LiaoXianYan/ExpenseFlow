import request from './request'

export function login(username: string, password: string) {
  return request.post('/system/auth/login', { username, password })
}
