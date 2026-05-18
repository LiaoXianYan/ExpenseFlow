<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>{{ title }}</h2>
        <p class="subtitle">{{ subtitle }}</p>
      </div>
      <div class="header-actions">
        <slot name="header-actions" />
      </div>
    </div>

    <SummaryCardRow v-if="summaryCards?.length" :cards="summaryCards" />

    <el-card class="table-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input
            v-if="searchable !== false"
            v-model="searchKeywordModel"
            placeholder="搜索..."
            clearable
            style="width: 240px"
            @keyup.enter="$emit('search')"
            @clear="$emit('search')"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <slot name="toolbar-filters" />
        </div>
        <div class="toolbar-right">
          <slot name="toolbar-actions" />
        </div>
      </div>

      <el-table
        :data="data" v-loading="loading" stripe class="data-table"
        @selection-change="(rows: any[]) => $emit('selectionChange', rows)"
      >
        <el-table-column v-if="selectable" type="selection" width="50" />
        <template v-for="col in columns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="col.label"
            :width="col.width"
            :min-width="col.minWidth"
            :fixed="col.fixed"
          >
            <template v-if="col.slot" #default="{ row }">
              <slot :name="col.slot" :row="row" />
            </template>
          </el-table-column>
        </template>
        <el-table-column v-if="$slots['row-actions'] || rowActions?.length" label="操作" :width="rowActionsWidth ?? 240" fixed="right">
          <template #default="{ row }">
            <slot name="row-actions" :row="row">
              <template v-for="act in rowActions" :key="act.label">
                <el-button
                  v-if="!act.visible || act.visible(row)"
                  :type="act.type || 'primary'"
                  link
                  size="small"
                  @click="act.onClick(row)"
                >
                  {{ act.label }}
                </el-button>
              </template>
            </slot>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && data.length === 0" description="暂无数据" :image-size="100">
        <slot name="empty-action" />
      </el-empty>

      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPageModel"
          :total="total"
          :page-size="pageSize"
          layout="prev,pager,next,total"
          background
          @current-change="(p: number) => $emit('pageChange', p)"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import SummaryCardRow from './SummaryCardRow.vue'

export interface TableColumn {
  prop: string
  label: string
  width?: string
  minWidth?: string
  fixed?: string | boolean
  slot?: string
}

export interface RowAction {
  label: string
  type?: string
  visible?: (row: any) => boolean
  onClick: (row: any) => void
}

const props = withDefaults(defineProps<{
  title: string
  subtitle?: string
  columns: TableColumn[]
  data: any[]
  loading: boolean
  total: number
  page?: number
  pageSize?: number
  searchable?: boolean
  selectable?: boolean
  searchKeyword?: string
  summaryCards?: { label: string; value: string | number; color?: string }[]
  rowActions?: RowAction[]
  rowActionsWidth?: number
}>(), {
  subtitle: '',
  page: 1,
  pageSize: 10,
  searchable: true,
  selectable: false,
  searchKeyword: '',
  rowActionsWidth: 240,
})

const emit = defineEmits<{
  'update:searchKeyword': [val: string]
  'update:page': [val: number]
  search: []
  pageChange: [page: number]
  selectionChange: [rows: any[]]
}>()

const searchKeywordModel = computed({
  get: () => props.searchKeyword,
  set: (val) => emit('update:searchKeyword', val),
})

const currentPageModel = computed({
  get: () => props.page,
  set: (val) => emit('update:page', val),
})
</script>

<style scoped>
.page { }
.page-header {
  display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px;
}
.page-header h2 {
  font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0;
}
.subtitle { color: #94A3B8; font-size: 13px; margin: 4px 0 0; }
.header-actions { display: flex; gap: 8px; flex-shrink: 0; }
.table-card { border-radius: 14px; }
.toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px;
}
.toolbar-left { display: flex; align-items: center; gap: 12px; }
.toolbar-right { display: flex; gap: 8px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
