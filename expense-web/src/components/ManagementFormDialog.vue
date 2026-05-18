<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    @closed="$emit('closed')"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
      @submit.prevent=""
    >
      <el-form-item
        v-for="field in fields"
        :key="field.prop"
        :label="field.label"
        :prop="field.prop"
      >
        <el-input
          v-if="field.type === 'text' || !field.type"
          v-model="formData[field.prop]"
          :placeholder="field.placeholder ?? '请输入' + field.label"
        />
        <el-input-number
          v-else-if="field.type === 'number'"
          v-model="formData[field.prop]"
          :min="field.min ?? 0"
          :precision="field.precision ?? 2"
          style="width: 100%"
        />
        <el-select
          v-else-if="field.type === 'select'"
          v-model="formData[field.prop]"
          :placeholder="field.placeholder ?? '请选择' + field.label"
          style="width: 100%"
        >
          <el-option
            v-for="opt in field.options"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-else-if="field.type === 'date'"
          v-model="formData[field.prop]"
          type="date"
        />
        <el-input
          v-else-if="field.type === 'textarea'"
          v-model="formData[field.prop]"
          type="textarea"
          :rows="field.rows ?? 3"
          :placeholder="field.placeholder ?? '请输入' + field.label"
        />
        <el-switch
          v-else-if="field.type === 'switch'"
          v-model="formData[field.prop]"
        />
        <el-input
          v-else-if="field.type === 'password'"
          v-model="formData[field.prop]"
          type="password"
          show-password
          placeholder="留空则不修改密码"
        />
        <slot v-else-if="field.type === 'custom'" :name="'field-' + field.prop" :form="formData" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('cancel')" :disabled="submitting">取消</el-button>
      <el-button type="primary" @click="$emit('submit')" :loading="submitting">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'

export interface FormField {
  prop: string
  label: string
  type?: 'text' | 'number' | 'select' | 'date' | 'textarea' | 'switch' | 'password' | 'custom'
  placeholder?: string
  min?: number
  precision?: number
  rows?: number
  options?: { label: string; value: any }[]
}

defineProps<{
  visible: boolean
  title: string
  fields: FormField[]
  formData: Record<string, any>
  rules?: FormRules
  submitting?: boolean
  width?: string
  labelWidth?: string
  formRef?: FormInstance | null
}>()

defineEmits<{
  'update:visible': [value: boolean]
  submit: []
  cancel: []
  closed: []
}>()
</script>
