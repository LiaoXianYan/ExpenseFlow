import { getUserRoles } from './jwt'

// 角色→权限码映射（与后端 @PreAuthorize 一致）
const ROLE_PERMISSIONS: Record<string, string[]> = {
  SUPER_ADMIN: ['*'],
  FINANCE: ['approval:approve', 'approval:reject', 'expense:report:delete', 'expense:payment:create'],
  APPROVER: ['approval:approve', 'approval:reject'],
  CASHIER: ['expense:payment:create'],
  USER: [],
}

/**
 * 检查当前用户是否拥有指定角色或权限
 * 含 ':' 视为权限码，否则视为角色名
 */
export function hasPermission(required: string | string[]): boolean {
  const roles = getUserRoles()
  const list = Array.isArray(required) ? required : [required]
  return list.some(p => {
    if (p.includes(':')) {
      // 权限码 → 查映射表
      return roles.some(r =>
        ROLE_PERMISSIONS[r]?.includes(p) || ROLE_PERMISSIONS[r]?.includes('*')
      )
    }
    // 角色名 → 直接比较
    return roles.includes(p)
  })
}
