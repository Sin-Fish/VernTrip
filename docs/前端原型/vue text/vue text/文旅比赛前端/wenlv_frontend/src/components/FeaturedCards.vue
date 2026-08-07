<template>
  <div class="travelogue-block">
    <div class="travelogue-header">
      <h3 class="travelogue-title">AI 游记 · 从一处地方开始</h3>
      <p class="travelogue-sub">让每一段地方记忆，都成为值得抵达的理由。</p>
    </div>

    <div class="travelogue-row">
      <a
        v-for="img in scrollImages"
        :key="img.name"
        :href="img.link"
        target="_blank"
        rel="noopener noreferrer"
        class="travel-btn"
      >
        <img
          :src="'/images/' + img.name + '.jpg'"
          :alt="img.name"
          class="travel-img"
        />
      </a>
    </div>
  </div>
</template>

<script setup>
const scrollImages = [
  { name: 'hu',    link: 'http://xhslink.com/o/64hBIJIJy8u' },
  { name: 'cha',   link: 'http://xhslink.com/o/1Nk3Q7Ep64x' },
  { name: 'zhou',  link: 'http://xhslink.com/o/2odr9z80oJt' },
  { name: 'shan',  link: 'http://xhslink.com/o/AJR0QaAF320' },
  { name: 'cheng', link: 'http://xhslink.com/o/7mZoZFR7Gl1' },
]
</script>

<style scoped>
.travelogue-block{ padding-top:8px; }

.travelogue-header{ margin-bottom:12px; }
.travelogue-title{
  font-family:'Noto Serif SC', serif;
  font-weight:700; font-size:22px; color:#2f4f42;
  letter-spacing:2px; margin-bottom:4px;
}
.travelogue-sub{
  font-family:'Noto Serif SC', serif;
  font-weight:300; font-size:12px; color:var(--ink-soft);
  letter-spacing:2px;
}

/* 横向一排图片 */
.travelogue-row{
  display:flex;
  gap:12px;
  overflow-x:auto;
}

.travel-btn{
  flex:1 1 0;
  min-width:140px;
  display:block;
  border-radius:4px;
  overflow:hidden;
  cursor:pointer;
  background:rgba(255,255,255,.15);
  backdrop-filter:blur(2px);
  border:1px solid rgba(44,36,27,.05);
  transition:transform .4s ease, box-shadow .4s ease;
}
.travel-btn:hover{
  transform:translateY(-3px);
  box-shadow:0 6px 18px rgba(44,36,27,.08);
}
.travel-img{
  width:100%;
  height:310px;
  object-fit:cover;
  display:block;
}

@media (max-width:860px){
  .travelogue-row{ flex-wrap:wrap; }
  .travel-btn{ flex:1 1 120px; min-width:100px; }
  .travel-img{ height:240px; }
}
</style>
