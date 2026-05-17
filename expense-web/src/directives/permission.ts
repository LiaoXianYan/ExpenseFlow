import type { Directive } from 'vue'
import { usePermissionStore } from '@/stores/permission'

export const vPermission: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const permStore = usePermissionStore()
    if (!permStore.has(binding.value)) {
      el.remove()
    }
  }
}
