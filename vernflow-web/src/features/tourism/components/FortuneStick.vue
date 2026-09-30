<script setup>
defineProps({ number: { type: String, default: '' } })
</script>

<template>
  <svg class="fortune-stick" viewBox="0 0 64 360" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <path d="M3 29 32 3 61 29V355H3Z" fill="#d7a45e" stroke="#b98343" stroke-width="2" />
    <path d="M3 29 32 3 61 29V118H3Z" fill="#ae4934" />
    <path d="M7 31 32 9 57 31V111H7Z" fill="none" stroke="#c86b47" stroke-width="1.5" />
    <path d="M3 110Q17 94 33 109Q37 89 49 94Q60 94 61 105V355H3Z" fill="#e8bb77" stroke="#f5d49a" stroke-width="3" />
    <path d="M9 117Q19 106 34 119Q39 100 49 103Q56 103 58 112V353H9Z" fill="#dfad68" />
    <path d="M14 125 16 350M23 137 21 275M29 114 31 225M38 139 36 349M47 116 49 296M54 129 53 350" fill="none" stroke="#bb884a" stroke-opacity=".3" stroke-width="1" />
    <path d="M11 133 12 302M34 146 34 350M50 135 50 251" fill="none" stroke="#f5d6a2" stroke-opacity=".5" />
    <text x="32" y="195" text-anchor="middle" fill="#95422f" font-family="Georgia, serif" font-size="16" font-weight="700">{{ number }}</text>
  </svg>
</template>

<style scoped>
.fortune-stick { display: block; width: 100%; height: 100%; overflow: visible; }
</style>
