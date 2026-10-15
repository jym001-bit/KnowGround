/**
 * 带种子的伪随机数生成器（PRNG）与平滑噪声插值函数。
 * 保证相同 seed 下生成完全一致的物理抖动与手绘笔触，具备确定性。
 */

export function createSeededRandom(seed = 1) {
  let s = seed | 0;
  return function () {
    s |= 0;
    let t = Math.imul((s = (s + 0x6d2b79f5) | 0) ^ (s >>> 15), 1 | s);
    return (
      (((t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t) ^ (t >>> 14)) >>> 0) /
      0x100000000
    );
  };
}

export function createSeededNormal(seed, steps = 10) {
  let rng = createSeededRandom(seed);
  let table = [];
  for (let i = 0; i <= steps + 1; i++) {
    table.push(2 * rng() - 1);
  }
  return function (t) {
    let clamped = Math.max(0, Math.min(1, t)) * steps;
    let idx = Math.floor(clamped);
    let frac = clamped - idx;
    return table[idx] + (table[idx + 1] - table[idx]) * (frac * frac * (3 - 2 * frac));
  };
}
