<template>
  <div class="feature-strip">
    <!-- 四角卷云纹 -->
    <svg class="corner tl" viewBox="0 0 40 40" width="24" height="24" fill="none" stroke="#c9a86a" stroke-width="1">
      <path d="M2 20 Q2 2 20 2"/><path d="M8 20 Q8 8 20 8"/>
    </svg>
    <svg class="corner tr" viewBox="0 0 40 40" width="24" height="24" fill="none" stroke="#c9a86a" stroke-width="1">
      <path d="M2 20 Q2 2 20 2"/><path d="M8 20 Q8 8 20 8"/>
    </svg>
    <svg class="corner bl" viewBox="0 0 40 40" width="24" height="24" fill="none" stroke="#c9a86a" stroke-width="1">
      <path d="M2 20 Q2 2 20 2"/><path d="M8 20 Q8 8 20 8"/>
    </svg>
    <svg class="corner br" viewBox="0 0 40 40" width="24" height="24" fill="none" stroke="#c9a86a" stroke-width="1">
      <path d="M2 20 Q2 2 20 2"/><path d="M8 20 Q8 8 20 8"/>
    </svg>

    <!-- 内容区 -->
    <div class="strip-inner">
      <div
        v-for="(item, i) in features"
        :key="i"
        class="feature-group"
      >
        <div class="fg-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="#c9a86a" stroke-width="1.5" width="30" height="30">
            <template v-if="i === 0">
              <circle cx="12" cy="12" r="9"/>
              <path d="M14.5 9.5L10 10l-0.5 4.5L14 14z"/>
            </template>
            <template v-else-if="i === 1">
              <circle cx="5" cy="6" r="2"/>
              <circle cx="19" cy="18" r="2"/>
              <path d="M7 6h6a4 4 0 014 4v0a4 4 0 01-4 4H7"/>
            </template>
            <template v-else>
              <path d="M4 10h16a8 6 0 01-16 0z"/>
              <path d="M4 10a8 2 0 0116 0"/>
            </template>
          </svg>
        </div>
        <div class="fg-text">
          <span class="fg-title">{{ item.title }}</span>
          <span class="fg-sub">{{ item.sub }}</span>
        </div>
      </div>

    </div>

  </div>
</template>

<script setup>
const features = [
  { title: 'AI 个性规划', sub: '智能行程定制' },
  { title: '智能路线推荐', sub: '优化出行路径' },
  { title: '美食与住宿推荐', sub: '地道本地选择' },
]
</script>

<style scoped>
.feature-strip{
  position:relative;
  background:#33473d;
  padding:0 56px;
  margin-top:0;
  min-height:72px;
  display:flex;
  align-items:center;
  justify-content:center;
  overflow:hidden;
}
/* 做旧水墨渲染 */
.feature-strip::before{
  content:"";
  position:absolute;
  inset:0;
  pointer-events:none;
  z-index:0;
  background:
    radial-gradient(ellipse at 15% 50%, rgba(255,255,255,.06) 0%, transparent 50%),
    radial-gradient(ellipse at 70% 30%, rgba(255,255,255,.04) 0%, transparent 45%),
    radial-gradient(ellipse at 85% 70%, rgba(255,255,255,.05) 0%, transparent 40%),
    radial-gradient(ellipse at 40% 80%, rgba(0,0,0,.1) 0%, transparent 35%);
}

/* 四角卷云纹 */
.corner{
  position:absolute;
  z-index:1;
  pointer-events:none;
}
.corner.tl{ top:12px; left:12px; }
.corner.tr{ top:12px; right:12px; transform:scaleX(-1); }
.corner.bl{ bottom:12px; left:12px; transform:scaleY(-1); }
.corner.br{ bottom:12px; right:12px; transform:scale(-1,-1); }

/* 内容行 */
.strip-inner{
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:0;
  z-index:2;
  max-width:1100px;
  width:100%;
}

.feature-group{
  display:flex;
  align-items:center;
  gap:14px;
  padding:0 32px;
}

.fg-icon{
  flex-shrink:0;
  display:flex;
  align-items:center;
}

.fg-text{
  display:flex;
  flex-direction:column;
  gap:4px;
}

.fg-title{
  font-family:'Noto Serif SC', serif;
  font-weight:600;
  font-size:14px;
  color:#c9a86a;
  letter-spacing:1px;
  white-space:nowrap;
}

.fg-sub{
  font-family:'Noto Serif SC', serif;
  font-weight:300;
  font-size:11px;
  color:rgba(201,168,106,.6);
  letter-spacing:.5px;
  white-space:nowrap;
}

@media (max-width:900px){
  .feature-strip{
    padding:24px 16px;
    min-height:auto;
  }
  .strip-inner{
    flex-direction:column;
    gap:18px;
  }
  .feature-group{
    justify-content:flex-start;
    padding:0;
  }
  .corner{ display:none; }
}
</style>
