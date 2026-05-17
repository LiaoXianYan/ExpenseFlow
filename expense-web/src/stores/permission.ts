import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMyPermissions } from '@/api/permission'

export const usePermissionStore = defineStore('permission', () => {
  const codes = ref<string[]>([])

  async function fetchPermissions() {
    try {
      const res = await getMyPermissions()
      codes.value = res.data ?? []
    } catch {
      codes.value = []
    }
  }

  function has(code: string): boolean {
    return codes.value.includes(code) || codes.value.includes('*')
  }

  function hasAny(required: string[]): boolean {
    return required.some(c => has(c))
  }

  function reset() {
    codes.value = []
  }

  return { codes, fetchPermissions, has, hasAny, reset }
})
