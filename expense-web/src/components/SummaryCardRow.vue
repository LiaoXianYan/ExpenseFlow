<template>
  <el-row :gutter="16" class="summary-row">
    <el-col v-for="card in cards" :key="card.label" :span="Math.min(6, 24 / cards.length)">
      <div class="summary-card" :style="{ borderTopColor: card.color ?? '#3B82F6' }">
        <div class="summary-value">{{ card.value }}</div>
        <div class="summary-label">{{ card.label }}</div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
defineProps<{
  cards: { label: string; value: string | number; color?: string; icon?: string }[]
}>()
</script>

<style scoped>
.summary-row { margin-bottom: 16px; }
.summary-card {
  background: #fff; border-radius: 12px; padding: 18px 20px;
  border-top: 3px solid #3B82F6;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.summary-value { font-size: 28px; font-weight: 700; color: #1E293B; line-height: 1.2; }
.summary-label { font-size: 13px; color: #94A3B8; margin-top: 4px; }
</style>
