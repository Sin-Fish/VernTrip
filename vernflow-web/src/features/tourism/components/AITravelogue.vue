<template>
  <section class="travelogue">
    <!-- 标题行 -->
    <div class="tl-header">
      <div class="tl-title-row">
        <svg class="orn-l" viewBox="0 0 18 18" width="18" height="18" fill="none" stroke="#C8AE82" stroke-width="1.2" opacity=".6">
          <rect x="2" y="2" width="14" height="14" rx="2"/><circle cx="9" cy="9" r="3"/>
        </svg>
        <span class="orn-line"></span>
        <h2>AI游记</h2>
        <span class="orn-line"></span>
        <svg class="orn-r" viewBox="0 0 24 16" width="24" height="16" fill="none" stroke="#C8AE82" stroke-width="1.2" opacity=".6">
          <path d="M2 8 Q2 2 8 2 Q12 2 12 8 Q12 14 16 14 Q20 14 22 8"/>
        </svg>
      </div>
      <a class="tl-more" href="#">
        查看更多 <span class="more-arrow">›</span>
      </a>
    </div>

    <!-- 卡片网格 -->
    <div class="tl-grid">
      <article
        v-for="item in travels"
        :key="item.city"
        class="tl-card"
      >
        <div class="tl-cover">
          <img :src="item.image" :alt="item.city" class="tl-img" />
          <span class="tl-city-tag">{{ item.city }}</span>
          <span class="tl-play-btn"></span>
          <span class="tl-duration">{{ item.duration }}</span>
        </div>
        <div class="tl-body">
          <h3 class="tl-title">{{ item.title }}</h3>
          <p class="tl-desc">{{ item.desc }}</p>
          <div class="tl-footer">
            <span class="tl-views">AI生成 · {{ item.views }}</span>
            <svg class="tl-mountain" viewBox="0 0 26 18" width="26" height="18" fill="none" stroke="#C8AE82" stroke-width="1" opacity=".5">
              <path d="M2 16 L8 6 L13 12 L18 2 L24 16"/>
            </svg>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
const travels = [
  { city:'西湖', title:'两天一夜漫游西湖', desc:'一步一景，遇见诗意江南', duration:'06:42', views:'1.2万次浏览', image:'/images/tripcard1.jpg' },
  { city:'黄山', title:'黄山云海日出全攻略', desc:'奇松怪石间感受天地壮阔', duration:'08:15', views:'2.3万次浏览', image:'/images/tripcard2.jpg' },
  { city:'乌镇', title:'枕水江南乌镇慢生活', desc:'小桥流水里的千年时光', duration:'05:30', views:'1.8万次浏览', image:'/images/tripcard3.jpg' },
  { city:'敦煌', title:'大漠深处遇见千年敦煌', desc:'莫高窟壁画里的丝路故事', duration:'07:20', views:'3.1万次浏览', image:'/images/tripcard4.jpg' },
  { city:'稻城亚丁', title:'川西秘境稻城徒步指南', desc:'雪山草甸间的最后净土', duration:'10:05', views:'4.5万次浏览', image:'/images/tripcard5.jpg' },
  { city:'桂林', title:'山水甲天下桂林漫游记', desc:'漓江烟雨里的水墨画卷', duration:'04:55', views:'1.6万次浏览', image:'/images/tripcard6.jpg' },
]
</script>

<style scoped>
.travelogue{
  width:100%;
  max-width:1500px;
  margin:6px auto 0;
  position:relative;
  z-index:1;
  padding:0 20px;
}

/* 标题行 */
.tl-header{
  display:flex;
  align-items:center;
  justify-content:space-between;
  margin-bottom:24px;
}
.tl-title-row{
  display:flex;
  align-items:center;
  gap:0;
  margin-top:-14px;
}
.tl-title-row h2{
  font-family:'Noto Serif SC', serif;
  font-size:32px; font-weight:700;
  color:#332D27;
  margin:0; padding:0 10px;
}
.orn-line{
  display:block;
  width:16px; height:1px;
  background:rgba(190,160,120,.35);
}
.tl-more{
  font-family:'Noto Serif SC', serif;
  font-size:16px; color:#8D7B63;
  text-decoration:none;
  display:flex; align-items:center; gap:4px;
  transition:color .2s;
}
.tl-more:hover{ color:#5a4030; }
.more-arrow{ display:inline-block; transition:transform .2s; }
.tl-more:hover .more-arrow{ transform:translateX(2px); }

/* 网格 */
.tl-grid{
  display:grid;
  grid-template-columns:repeat(6,1fr);
  gap:18px;
}

/* 卡片 */
.tl-card{
  background:#F9F4EA;
  border-radius:14px;
  overflow:hidden;
  border:1px solid rgba(180,150,100,.18);
  box-shadow:0 6px 20px rgba(120,90,50,.06);
  transition:transform .3s ease, box-shadow .3s ease;
}
.tl-card:hover{
  transform:translateY(-6px);
  box-shadow:0 12px 30px rgba(120,90,50,.12);
}

/* 封面 */
.tl-cover{
  position:relative;
  height:130px;
  overflow:hidden;
}
.tl-img{
  width:100%; height:100%;
  object-fit:cover;
  display:block;
}
.tl-city-tag{
  position:absolute; top:0; left:0;
  background:#8D7357; color:#fff;
  border-radius:0 0 8px 0;
  padding:6px 10px;
  font-family:'Noto Serif SC', serif;
  font-size:14px;
}
.tl-play-btn{
  position:absolute; bottom:10px; left:10px;
  width:32px; height:32px;
  border-radius:50%;
  background:rgba(0,0,0,.45);
  cursor:pointer;
}
.tl-play-btn::after{
  content:""; display:block;
  width:0; height:0;
  border-left:8px solid #fff;
  border-top:5px solid transparent;
  border-bottom:5px solid transparent;
  position:absolute; top:50%; left:55%;
  transform:translate(-50%,-50%);
}
.tl-duration{
  position:absolute; bottom:10px; right:10px;
  background:rgba(0,0,0,.55); color:#fff;
  border-radius:4px;
  padding:3px 6px;
  font-family:'Noto Serif SC', serif;
  font-size:13px;
}

/* 内容 */
.tl-body{ padding:10px; }
.tl-title{
  font-family:'Noto Serif SC', serif;
  font-size:17px; font-weight:600;
  color:#332D27;
  line-height:1.3;
  margin:0 0 4px;
  display:-webkit-box;
  -webkit-line-clamp:2;
  -webkit-box-orient:vertical;
  overflow:hidden;
}
.tl-desc{
  font-family:'Noto Serif SC', serif;
  font-size:13px; color:#8D7B63;
  margin:0 0 8px;
  white-space:nowrap;
  overflow:hidden;
  text-overflow:ellipsis;
}
.tl-footer{
  display:flex;
  align-items:center;
  justify-content:space-between;
}
.tl-views{
  font-family:'Noto Serif SC', serif;
  font-size:13px; color:#9A8A73;
}
.tl-mountain{ flex-shrink:0; }

/* 响应式 */
@media (max-width:1440px){ .tl-grid{ grid-template-columns:repeat(5,1fr); } }
@media (max-width:1200px){ .tl-grid{ grid-template-columns:repeat(4,1fr); } }
@media (max-width:992px){ .tl-grid{ grid-template-columns:repeat(3,1fr); } }
@media (max-width:768px){ .tl-grid{ grid-template-columns:repeat(2,1fr); } }
@media (max-width:576px){ .tl-grid{ grid-template-columns:1fr; } }
</style>
