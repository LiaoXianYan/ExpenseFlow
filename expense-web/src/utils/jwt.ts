export interface JwtPayload {
  sub: string
  tenantId: number
  roles: string[]
  username: string
  exp: number
}

export function parseToken(token: string): JwtPayload | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload as JwtPayload
  } catch {
    return null
  }
}

export function getUserRoles(): string[] {
  const token = localStorage.getItem('token')
  if (!token) return []
  const payload = parseToken(token)
  return payload?.roles || []
}

export function hasAnyRole(required: string[]): boolean {
  const roles = getUserRoles()
  return required.some(r => roles.includes(r))
}
