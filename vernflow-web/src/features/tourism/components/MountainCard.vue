<template>
  <div class="mountain-card">
    <img src="/images/mountain-card-bg.png" alt="" class="card-bg" />
    <div class="sign-overlay">
      <div v-for="(card, i) in cards" :key="i" class="sign-card" :class="'rot-' + i">
        <img src="/images/sign-card.png" alt="" class="sign-img" />
      </div>
    </div>
  </div>
</template>

<script setup>
const cards = [0, 1, 2]
</script>

<style scoped>
.mountain-card{
  position:relative;
  width:100%;
  max-width:580px;
  height:430px;
  border-radius:20px;
  overflow:hidden;
  box-shadow:0 8px 25px rgba(120,90,50,.08);
}
.card-bg{
  position:absolute;
  inset:0;
  width:100%;
  height:100%;
  object-fit:cover;
  display:block;
  z-index:0;
}

/* 签牌浮层 */
.sign-overlay{
  position:absolute;
  top:50%; left:50%;
  transform:translate(-50%,-50%);
  display:flex;
  flex-direction:row;
  justify-content:center;
  align-items:center;
  gap:10px;
  z-index:1;
}
.sign-card{
  width:140px;
  height:240px;
  border-radius:6px;
  box-shadow:0 8px 20px rgba(80,60,40,.18);
  overflow:hidden;
  transition:transform .3s ease;
}
.sign-card.rot-0{  }
.sign-card.rot-1{  }
.sign-card.rot-2{  }
.sign-card:hover{ transform:translateY(-6px); }
.sign-img{
  width:100%; height:100%;
  object-fit:cover;
  display:block;
}

@media (max-width:1100px){
  .mountain-card{ height:380px; }
  .sign-card{ width:110px; height:200px; }
}
</style>
