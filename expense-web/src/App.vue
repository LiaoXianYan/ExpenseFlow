<template>
  <router-view />
</template>

<style>
/* ===== Global Reset & Typography ===== */
* { margin: 0; padding: 0; box-sizing: border-box; }

html, body, #app {
  height: 100%;
  font-family: 'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #1e293b;
  background: #f1f5f9;
}

/* ===== Element Plus Overrides ===== */
:root {
  --el-color-primary: #3B82F6;
  --el-color-primary-light-3: #60a5fa;
  --el-color-primary-dark-2: #2563eb;
  --el-menu-bg-color: #0f1a2e;
  --el-menu-text-color: #94a3b8;
  --el-menu-hover-bg-color: rgba(255,255,255,0.06);
  --el-menu-active-color: #F59E0B;
  --el-border-radius-base: 8px;
  --el-font-family: 'IBM Plex Sans', system-ui, sans-serif;
}

/* Card polish */
.el-card {
  border-radius: 12px !important;
  border: none !important;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04) !important;
  transition: box-shadow 0.2s;
}

.el-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08), 0 2px 4px rgba(0,0,0,0.04) !important;
}

/* Button polish */
.el-button--primary {
  border-radius: 8px;
  font-weight: 500;
  letter-spacing: 0.3px;
}

/* Table polish */
.el-table {
  border-radius: 12px;
  overflow: hidden;
}

.el-table th.el-table__cell {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
  font-size: 13px;
}

/* Input polish */
.el-input__wrapper {
  border-radius: 8px;
}

/* Tag polish */
.el-tag {
  border-radius: 6px; font-weight: 500;
}

/* Page header */
.page-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px; font-weight: 700; color: #1E3A5F;
}
</style>
