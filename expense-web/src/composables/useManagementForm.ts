import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

export interface FormOptions<T extends Record<string, any>> {
  createApi?: (data: T) => Promise<any>
  updateApi?: (id: number, data: T) => Promise<any>
  getDetailApi?: (id: number) => Promise<any>
  defaultForm: T
  rules?: FormRules
  onSuccess: () => void
}

export function useManagementForm<T extends Record<string, any>>(options: FormOptions<T>) {
  const dialogVisible = ref(false)
  const dialogTitle = ref('新建')
  const isEdit = ref(false)
  const formData = reactive<T>({ ...options.defaultForm }) as T
  const formRef = ref<FormInstance>()
  const submitting = ref(false)
  let editId: number | null = null

  function handleCreate() {
    dialogTitle.value = '新建'
    isEdit.value = false
    editId = null
    Object.assign(formData, { ...options.defaultForm })
    formRef.value?.resetFields()
    dialogVisible.value = true
  }

  async function handleEdit(id: number) {
    dialogTitle.value = '编辑'
    isEdit.value = true
    editId = id
    if (options.getDetailApi) {
      try {
        const res = await options.getDetailApi(id)
        Object.assign(formData, res.data ?? {})
      } catch {
        return
      }
    }
    formRef.value?.resetFields()
    dialogVisible.value = true
  }

  async function handleSubmit() {
    if (!formRef.value) return
    try {
      await formRef.value.validate()
    } catch {
      return
    }
    submitting.value = true
    try {
      if (isEdit.value && options.updateApi && editId !== null) {
        await options.updateApi(editId, { ...formData })
        ElMessage.success('更新成功')
      } else if (options.createApi) {
        await options.createApi({ ...formData })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      options.onSuccess()
    } catch {
      // error handled by interceptor
    } finally {
      submitting.value = false
    }
  }

  function handleClose() {
    dialogVisible.value = false
  }

  return {
    dialogVisible, dialogTitle, isEdit, formData, formRef, submitting,
    handleCreate, handleEdit, handleSubmit, handleClose,
  }
}
