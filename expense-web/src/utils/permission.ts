import { getUserRoles } from './jwt'
import { usePermissionStore } from '@/stores/permission'

/**
 * Check if the current user has the specified permission code or role.
 * Codes with ':' are treated as permission codes, otherwise as role names.
 */
export function hasPermission(required: string | string[]): boolean {
  const permStore = usePermissionStore()
  const list = Array.isArray(required) ? required : [required]
  return list.some(p => {
    if (p.includes(':')) {
      return permStore.has(p)
    }
    // Fallback: treat as role name
    const roles = getUserRoles()
    return roles.includes(p)
  })
}
