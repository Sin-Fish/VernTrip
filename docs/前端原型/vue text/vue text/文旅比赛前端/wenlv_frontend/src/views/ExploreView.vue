<template>
  <div class="explore-page">
    <img src="/images/explorenew2.jpg" alt="" class="explore-bg-img" />
    <div class="explore-body">
      <div class="explore-main">
        <HeroCarousel :slides="banners" class="carousel-pos hero-left" />
      </div>
      <aside class="explore-sidebar">
        <MountainCard />
      </aside>
    </div>
    <AITravelogue />
  </div>
</template>

<script setup>
import HeroCarousel from '../components/HeroCarousel.vue'
import MountainCard from '../components/MountainCard.vue'
import AITravelogue from '../components/AITravelogue.vue'

const banners = [
  '/images/banner1.jpg',
  '/images/banner2.jpg',
  '/images/banner3.jpg',
  '/images/banner4.jpg',
  '/images/banner5.jpg',
]
</script>

<style scoped>
.explore-page{
  position:relative;
  min-height:100vh;
}
.explore-bg-img{
  position:absolute;
  top:0; left:0;
  width:100%;
  height:100%;
  object-fit:cover;
  object-position:top center;
  z-index:0;
}
.explore-body{
  position:relative;
  z-index:1;
  max-width:1500px;
  margin:0 auto;
  padding:18px 40px 40px 40px;
  display:flex;
  gap:20px;
  align-items:stretch;
}
.carousel-pos{ margin-left:0; }
.hero-left{ width:100%; }
.explore-main{ width:58%; max-width:900px; min-width:600px; }
.explore-sidebar{
  flex:1;
  max-width:580px;
  min-width:400px;
}

@media (max-width:768px){
  .explore-body{
    flex-direction:column;
    padding:20px 16px;
  }
}
</style>
