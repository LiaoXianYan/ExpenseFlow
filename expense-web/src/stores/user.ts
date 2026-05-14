import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

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

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, isLoggedIn, setToken, setUserInfo, logout }
})
