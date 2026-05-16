import { describe, it, expect } from 'vitest'

// Directly test the mapping logic (inlined from permission.ts)
const ROLE_PERMISSIONS: Record<string, string[]> = {
  SUPER_ADMIN: ['*'],
  FINANCE: ['approval:approve', 'approval:reject', 'expense:report:delete', 'expense:payment:create'],
  APPROVER: ['approval:approve', 'approval:reject'],
  CASHIER: ['expense:payment:create'],
  USER: [],
}

function hasPermission(roles: string[], required: string | string[]): boolean {
  const list = Array.isArray(required) ? required : [required]
  return list.some(p => {
    if (p.includes(':')) {
      return roles.some(r =>
        ROLE_PERMISSIONS[r]?.includes(p) || ROLE_PERMISSIONS[r]?.includes('*')
      )
    }
    return roles.includes(p)
  })
}

describe('hasPermission', () => {
  it('角色匹配时返回 true', () => {
    expect(hasPermission(['FINANCE'], ['FINANCE'])).toBe(true)
  })

  it('角色不匹配时返回 false', () => {
    expect(hasPermission(['USER'], ['FINANCE'])).toBe(false)
  })

  it('权限码匹配时返回 true（FINANCE 有 expense:payment:create）', () => {
    expect(hasPermission(['FINANCE'], 'expense:payment:create')).toBe(true)
  })

  it('SUPER_ADMIN 通配符 * 匹配任意权限码', () => {
    expect(hasPermission(['SUPER_ADMIN'], 'any:random:permission')).toBe(true)
  })

  it('数组任一角色满足即返回 true', () => {
    expect(hasPermission(['USER'], ['USER', 'FINANCE'])).toBe(true)
  })

  it('USER 角色无 approve 权限', () => {
    expect(hasPermission(['USER'], 'approval:approve')).toBe(false)
  })
})
