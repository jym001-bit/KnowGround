<template>
  <main class="missing-page">
    <div class="missing-code" aria-hidden="true">
      <span class="missing-digit">4</span>
      <svg class="missing-ghost" viewBox="0 0 134 166" fill="none" xmlns="http://www.w3.org/2000/svg">
        <g stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M31 65C32 30 45 9 67 8c23-1 35 21 36 56 6-8 10-14 16-11 6 3 8 20 12 26-4 8-10 10-17 6-3 11-8 16-15 17v27c0 6-4 11-10 10-5 0-6-7-8-8-2 3-4 16-11 17-6 1-9-7-12-8-4 4-8 12-14 10-7-2-7-10-7-16-8-2-13-8-16-17-7 3-15 0-18-6 3-8 4-24 11-29 6-4 11 3 17 11Z" />
          <path d="M29 67c-4 16-4 31 5 42M103 67c4 16 4 31-5 42M41 43c3-15 10-24 21-27M52 110v28M81 108v23M20 69c1 5 5 10 10 11M114 81c5 1 9-2 11-6" opacity=".72" />
          <ellipse cx="56" cy="55" rx="3.4" ry="6.5" fill="currentColor" stroke="none" />
          <ellipse cx="78" cy="55" rx="3.4" ry="6.5" fill="currentColor" stroke="none" />
          <path d="M66 73c2 2 4 2 6 0" />
        </g>
      </svg>
      <span class="missing-digit">4</span>
    </div>
    <h1>Boo! 页面不见了</h1>
    <p class="missing-description">你要找的页面，像幽灵一样不在这里。</p>
    <details class="missing-help">
      <summary>什么是 404？</summary>
      <p>这个地址没有对应的页面，请检查链接是否正确。</p>
    </details>
  </main>
</template>

<style scoped>
.missing-page { box-sizing: border-box; min-height: 100svh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px 56px; background: rgb(var(--layout-bg-color)); color: rgb(var(--base-text-color)); font-family: var(--kg-font-sans); text-align: center; }
.missing-page *, .missing-page *::before, .missing-page *::after { box-sizing: border-box; }
.missing-code { display: flex; align-items: center; justify-content: center; gap: clamp(14px, 2.6vw, 38px); height: 185px; margin-bottom: 44px; color: rgb(var(--text-color-2)); }
.missing-digit { font-size: clamp(108px, 12vw, 164px); font-weight: 650; line-height: 1; letter-spacing: -.08em; }
.missing-ghost { width: clamp(92px, 10vw, 138px); height: auto; margin: 5px 0 0; animation: float 4s ease-in-out infinite; }
h1 { margin: 0; font-size: clamp(36px, 5.4vw, 68px); font-weight: 740; letter-spacing: -.055em; line-height: 1.17; }
.missing-description { margin: 24px 0 0; color: rgb(var(--text-color-2)); font-size: clamp(17px, 2vw, 26px); line-height: 1.55; }
summary:focus-visible { outline: 3px solid rgb(var(--primary-color)); outline-offset: 5px; }
.missing-help { margin-top: 70px; color: rgb(var(--text-color-3)); font-size: 21px; }
summary { cursor: pointer; text-decoration: underline; text-underline-offset: 5px; list-style: none; }
summary::-webkit-details-marker { display: none; }
.missing-help p { max-width: 360px; margin: 17px auto 0; color: rgb(var(--text-color-2)); font-size: 15px; line-height: 1.7; }
@keyframes float { 0%, 100% { transform: translateY(0) rotate(-2deg); } 50% { transform: translateY(-9px) rotate(2deg); } }
@media (max-width: 600px) { .missing-page { padding: 36px 20px; } .missing-code { height: 125px; margin-bottom: 28px; } .missing-description { margin-top: 18px; } .missing-help { margin-top: 54px; font-size: 17px; } }
@media (prefers-reduced-motion: reduce) { .missing-ghost { animation: none; } }
</style>
