<script setup lang="ts">
defineProps<{ name: string }>()
const paths: Record<string, string> = {
  overview: 'M12 21S3 15.5 3 8.5a4.5 4.5 0 0 1 9-1 4.5 4.5 0 0 1 9 1C21 15.5 12 21 12 21Z M8 10v1 M16 10v1',
  content: 'M14 3H5v18h14V8L14 3Z M14 3v5h5 M8 12h8 M8 16h6',
  home: 'M3 11 12 3l9 8 M5 10v11h14V10 M9 21v-7h6v7',
  ad: 'M3 5h18v14H3V5Z M10 9l5 3-5 3V9Z',
  feedback: 'M3 4h18v13H9l-6 4V4Z M7 8h10 M7 12h7',
  legal: 'M5 3h14v18H5V3Z M8 7h8 M8 11h8 M8 15h5',
  ai: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z',
  category: 'M3 3h8l10 10-8 8L3 11V3Z M7 7h.01',
  material: 'M3 3h18v18H3V3Z M3 17l6-6 4 4 3-3 5 5 M16 7h.01',
  calendar: 'M4 5h16v16H4V5Z M8 3v4 M16 3v4 M4 10h16 M8 14h2 M14 14h2 M8 17h2',
  analytics: 'M4 21V11h4v10 M10 21V3h4v18 M16 21V7h4v14',
  audit: 'M5 3h14v18H5V3Z M8 7h8 M8 11h8 M8 15h5',
  settings: 'm9 3-1 3-3 1-2 3 2 2-1 3 3 2 3-1 2 3 3-1 1-3 3-1 2-3-2-2 1-3-3-2-3 1-2-2H9Z M9 12a3 3 0 1 0 6 0 3 3 0 1 0-6 0',
}
</script>

<template>
  <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path :d="paths[name]" /></svg>
</template>
