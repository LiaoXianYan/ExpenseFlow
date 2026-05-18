import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export interface TableOptions {
  fetchApi: (params: Record<string, any>) => Promise<any>
  deleteApi?: (id: number) => Promise<any>
  defaultPageSize?: number
}

export function useManagementTable(options: TableOptions) {
  const tableData = ref<any[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(options.defaultPageSize || 10)
  const searchKeyword = ref('')

  async function loadData() {
    loading.value = true
    try {
      const res = await options.fetchApi({
        page: page.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
      })
      tableData.value = res.data?.records ?? []
      total.value = res.data?.total ?? 0
    } catch {
      tableData.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function onSearch() {
    page.value = 1
    loadData()
  }

  function onReset() {
    searchKeyword.value = ''
    page.value = 1
    loadData()
  }

  function onPageChange(p: number) {
    page.value = p
    loadData()
  }

  function onSizeChange(s: number) {
    pageSize.value = s
    page.value = 1
    loadData()
  }

  async function handleDelete(id: number, label?: string) {
    if (!options.deleteApi) return
    try {
      await ElMessageBox.confirm(`确认删除${label ?? '该项'}？`, '删除确认', {
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await options.deleteApi(id)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      // error handled by interceptor
    }
  }

  return {
    tableData, loading, total, page, pageSize, searchKeyword,
    loadData, onSearch, onReset, onPageChange, onSizeChange, handleDelete,
  }
}
