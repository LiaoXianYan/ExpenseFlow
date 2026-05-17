import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePermissionStore } from './permission'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<any>(null)

  const isLoggedIn = computed(() => !!token.value)

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('token', t)
  }

  function setUserInfo(info: any) {
    userInfo.value = info
  }

  async function login(t: string, info: any) {
    setToken(t)
    setUserInfo(info)
    // 拉取权限码
    const permStore = usePermissionStore()
    await permStore.fetchPermissions()
  }

  function logout() {
    const permStore = usePermissionStore()
    permStore.reset()
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, isLoggedIn, setToken, setUserInfo, login, logout }
})
