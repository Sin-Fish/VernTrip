<template>
  <div class="fortune-block">
    <div class="fortune-header">
      <h3 class="fortune-title">今日山水签</h3>
      <p class="fortune-sub">抽一签，看看山水想带你去哪里。</p>
    </div>

    <div class="fortune-row">
      <!-- 签纸 — 图片 -->
      <div class="lot-paper" :class="{ drawn: isDrawn }">
        <img src="/images/fortune-lot.png" alt="抽签" class="lot-img" />

        <div v-if="!isDrawn" class="lot-default">
          <button class="draw-btn" @click="drawLot">抽签</button>
        </div>

        <div v-else class="lot-result">
          <p class="lot-poem">{{ currentLot.poem }}</p>
          <div class="lot-divider"></div>
          <p class="lot-interpret" v-if="showInterpret">{{ currentLot.interpret }}</p>
          <p v-else class="lot-loading">AI 解签中…</p>
        </div>
      </div>

      <!-- 推荐目的地 -->
      <div class="lot-destinations" v-if="isDrawn && showDestinations">
        <p class="dest-label">AI 为你推荐</p>
        <div class="dest-list">
          <a v-for="d in currentLot.destinations" :key="d.name" class="dest-item" href="#">
            <span class="dest-dot"></span>
            <span class="dest-name">{{ d.name }}</span>
            <span class="dest-desc">{{ d.desc }}</span>
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const isDrawn = ref(false)
const showInterpret = ref(false)
const showDestinations = ref(false)
const currentLot = ref(null)

const lots = [
  {
    poem: '宜向西南，\n寻一处有水的地方。',
    interpret: '你可能不是想去远方，\n只是想暂时离开熟悉的生活。',
    destinations: [
      { name: '贵州 · 荔波', desc: '地球腰带上的绿宝石' },
      { name: '浙江 · 松阳', desc: '最后的江南秘境' },
      { name: '湖南 · 张家界', desc: '悬浮山间的云雾之境' },
    ],
  },
  {
    poem: '宜向山行，\n莫问归期。',
    interpret: '山不会问你从哪里来，\n它只会静静地等在那里。',
    destinations: [
      { name: '安徽 · 黄山', desc: '云海之上的东方诗意' },
      { name: '江西 · 武功山', desc: '高山草甸的星空帐篷' },
      { name: '四川 · 四姑娘山', desc: '东方阿尔卑斯的秋色' },
    ],
  },
  {
    poem: '宜向古巷，\n寻一味人间烟火。',
    interpret: '走得越远，越想念那些\n藏在巷子深处的老味道。',
    destinations: [
      { name: '浙江 · 绍兴', desc: '乌篷船上的黄酒香' },
      { name: '广东 · 潮州', desc: '工夫茶里的慢时光' },
      { name: '云南 · 大理', desc: '古城墙下的人间至味' },
    ],
  },
  {
    poem: '宜向湖心，\n听一夜雨打荷叶。',
    interpret: '有些风景不需要晴天，\n雨中的江南才是真江南。',
    destinations: [
      { name: '浙江 · 西湖', desc: '晴雨皆是画中游' },
      { name: '江苏 · 太湖', desc: '三万六千顷的烟波' },
      { name: '湖南 · 洞庭湖', desc: '岳阳楼外的水天一色' },
    ],
  },
  {
    poem: '宜向草原，\n把心事还给风。',
    interpret: '城市的边界感在这里消失，\n天地之间只剩下你。',
    destinations: [
      { name: '内蒙古 · 呼伦贝尔', desc: '风吹草低见牛羊' },
      { name: '新疆 · 那拉提', desc: '空中草原的牧歌' },
      { name: '四川 · 若尔盖', desc: '川西高原的花海' },
    ],
  },
]

function drawLot() {
  const idx = Math.floor(Math.random() * lots.length)
  currentLot.value = lots[idx]
  isDrawn.value = true
  showInterpret.value = false
  showDestinations.value = false
  setTimeout(() => { showInterpret.value = true }, 1800)
  setTimeout(() => { showDestinations.value = true }, 3200)
}
</script>

<style scoped>
.fortune-block{ padding-top:8px; }

.fortune-header{ margin-bottom:14px; }
.fortune-title{
  font-family:'Noto Serif SC', serif;
  font-weight:700; font-size:24px; color:#2f4f42;
  letter-spacing:2px; margin-bottom:5px;
}
.fortune-sub{
  font-family:'Noto Serif SC', serif;
  font-weight:300; font-size:12.5px; color:var(--ink-soft);
  letter-spacing:2px;
}

.fortune-row{ display:flex; gap:28px; align-items:flex-start; }

/* 签纸图片 */
.lot-paper{
  flex:0 0 240px;
  position:relative;
  transition:transform .5s ease;
  line-height:0;
  filter:drop-shadow(0 4px 12px rgba(45,48,45,.12));
}
.lot-paper.drawn{
  transform:translateY(-4px);
}
.lot-img{
  width:100%;
  height:auto;
  display:block;
}

.lot-default{
  position:absolute;
  top:48%; left:56%;
  transform:translate(-50%,-50%);
}
.draw-btn{
  padding:0;
  border:none;
  background:transparent;
  writing-mode:vertical-rl;
  color:var(--paper);
  padding:0;
  border:none;
  background:transparent;
  writing-mode:vertical-rl;
  color:#F4EFE6;
  font-family:'Noto Serif SC', serif;
  font-weight:600;
  font-size:22px; letter-spacing:4px; cursor:pointer;
  text-shadow:0 1px 3px rgba(0,0,0,.15);
}

.lot-result{
  position:absolute;
  top:0; right:0; bottom:0; left:0;
  display:flex; flex-direction:column;
  align-items:center; justify-content:center;
  padding:24px 16px;
  text-align:center;
  background:rgba(245,240,230,.72);
}
.lot-poem{
  font-family:'Noto Serif SC', serif;
  font-weight:700; font-size:15px;
  color:#1a1a1a; line-height:2; letter-spacing:1.5px; white-space:pre-line;
}
.lot-divider{
  width:28px; height:1px;
  background:rgba(44,36,27,.15); margin:12px auto;
}
.lot-interpret{
  font-family:'Noto Serif SC', serif;
  font-weight:400; font-size:12.5px;
  color:var(--ink); line-height:1.8; letter-spacing:2px; white-space:pre-line;
}
.lot-loading{
  font-family:'Noto Serif SC', serif;
  font-weight:300; font-size:12.5px; color:var(--ink-light); letter-spacing:2px; animation:pulse 1.5s infinite;
}
@keyframes pulse{ 0%,100%{opacity:.35;} 50%{opacity:1;} }

/* 目的地 */
.lot-destinations{ flex:1; min-width:0; }
.dest-label{
  font-family:'Noto Serif SC', serif;
  font-weight:300; font-size:12px; color:var(--ink-soft);
  letter-spacing:2px; margin-bottom:10px;
}
.dest-list{ display:flex; flex-direction:column; gap:8px; }
.dest-item{
  display:flex; align-items:center; gap:8px;
  padding:10px 14px;
  background:rgba(255,255,255,.15);
  backdrop-filter:blur(2px);
  border:1px solid rgba(44,36,27,.05);
  border-radius:8px;
  text-decoration:none; transition:all .4s ease;
}
.dest-item:hover{ border-color:rgba(44,36,27,.15); }
.dest-dot{
  width:5px; height:5px; border-radius:50%;
  background:var(--cinnabar); opacity:.5; flex-shrink:0;
}
.dest-name{
  font-family:'Noto Serif SC', serif;
  font-weight:700; font-size:13.5px; color:var(--ink); letter-spacing:2px; white-space:nowrap;
}
.dest-desc{
  font-family:'Noto Serif SC', serif;
  font-weight:400; font-size:12px; color:var(--ink-soft); letter-spacing:2px; margin-left:auto;
}

@media (max-width:640px){
  .fortune-row{ flex-direction:column; align-items:stretch; }
  .lot-paper{ flex:0 0 auto; max-width:280px; }
}
</style>
