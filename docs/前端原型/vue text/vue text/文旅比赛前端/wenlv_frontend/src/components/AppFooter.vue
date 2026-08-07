<template>
  <footer class="site-footer">
    <div class="seal"><img src="/images/logo.jpg" alt="山水灵" /></div>
    <p>SHANSHUILING · EST. 2026</p>
  </footer>
</template>

<script setup>
</script>

<style scoped>
.site-footer{
  display:flex;
  align-items:center;
  justify-content:center;
  gap:16px;
  padding:44px 0 56px;
  border-top:1px solid rgba(119,115,107,.12);
}
.seal{
  width:44px; height:44px;
  display:flex; align-items:center; justify-content:center;
}
.seal img{
  width:100%; height:100%;
  object-fit:contain;
  mix-blend-mode:multiply;
}
.site-footer p{
  font-family:'Noto Sans SC', sans-serif;
  font-size:11px;
  letter-spacing:3px;
  color:var(--ink-soft);
  font-weight:300;
}
</style>
