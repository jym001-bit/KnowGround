import { createSeededNormal } from "./prng.js";

export const MARKER_FILTER_ID = "mk-marker";
export const DRY_FILTER_ID = "mk-dry";
export const RULE_FILTER_ID = "mk-rule";

/**
 * 确定性哈希噪声函数（返回 0 ~ 1 的浮点数）
 */
export function hashNoise(value) {
  const n = 43758.5453 * Math.sin(127.1 * value + 311.7);
  return n - Math.floor(n);
}

// 快速查找噪声表
const NOISE_TABLE = (() => {
  const table = new Float32Array(1024);
  for (let i = 0; i < 1024; i++) {
    const n = 43758.5453 * Math.sin(127.1 * i + 311.7);
    table[i] = n - Math.floor(n);
  }
  return table;
})();

export function fastNoise(value) {
  return NOISE_TABLE[(733.1 * value) & 1023];
}

export function clampRange(val, min, max) {
  return Math.max(min, Math.min(max, val));
}

export function smoothStep(t) {
  return t * t * (3 - 2 * t);
}

/**
 * 墨水颜色暗化函数（将 HEX 颜色按比例暗化）
 */
export function darkenColor(colorHex, factor) {
  if (!colorHex) return colorHex;
  const hex = colorHex.trim();
  let rgb = null;
  const shortMatch = /^#([0-9a-f]{3})$/i.exec(hex);
  if (shortMatch) {
    rgb = [
      17 * parseInt(shortMatch[1][0], 16),
      17 * parseInt(shortMatch[1][1], 16),
      17 * parseInt(shortMatch[1][2], 16),
    ];
  } else {
    const longMatch = /^#([0-9a-f]{6})$/i.exec(hex);
    if (longMatch) {
      rgb = [
        parseInt(longMatch[1].slice(0, 2), 16),
        parseInt(longMatch[1].slice(2, 4), 16),
        parseInt(longMatch[1].slice(4, 6), 16),
      ];
    }
  }
  if (!rgb) return colorHex;
  const max = Math.max(rgb[0], rgb[1], rgb[2]);
  return (
    "rgb(" +
    rgb
      .map((c) => Math.max(0, Math.round((c + (c - max) * 0.25) * (1 - factor))))
      .join(",") +
    ")"
  );
}

/**
 * 计算折线总长度
 */
export function getPolylineLength(points) {
  let len = 0;
  for (let i = 1; i < points.length; i++) {
    len += Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]);
  }
  return len;
}

/**
 * 沿折线按弧长重采样
 */
export function resamplePolyline(points, sampleCount) {
  sampleCount = Math.max(2, Math.min(2048, Math.round(Number(sampleCount) || 2)));
  if (points.length < 2) return points.slice();
  const cumulative = [0];
  let total = 0;
  for (let i = 1; i < points.length; i++) {
    total += Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]);
    cumulative.push(total);
  }
  if (total === 0) return points.slice();

  const resampled = [];
  let currIdx = 0;
  for (let i = 0; i < sampleCount; i++) {
    const targetDist = (total * i) / (sampleCount - 1);
    while (currIdx < cumulative.length - 2 && cumulative[currIdx + 1] < targetDist) {
      currIdx++;
    }
    const segmentLen = cumulative[currIdx + 1] - cumulative[currIdx] || 1;
    const progress = (targetDist - cumulative[currIdx]) / segmentLen;
    resampled.push([
      points[currIdx][0] + (points[currIdx + 1][0] - points[currIdx][0]) * progress,
      points[currIdx][1] + (points[currIdx + 1][1] - points[currIdx][1]) * progress,
    ]);
  }
  return resampled;
}

/**
 * 计算每个点的切线单位法向量
 */
export function getPolylineNormals(points) {
  const len = points.length;
  const normals = [];
  for (let i = 0; i < len; i++) {
    const prev = points[Math.max(0, i - 1)];
    const next = points[Math.min(len - 1, i + 1)];
    const dx = next[0] - prev[0];
    const dy = next[1] - prev[1];
    const dist = Math.hypot(dx, dy) || 1;
    normals.push([dx / dist, dy / dist]);
  }
  return normals;
}

/**
 * 沿法线偏移路径
 */
export function offsetPolyline(points, offsetFn) {
  const normals = getPolylineNormals(points);
  const count = points.length;
  const result = [];
  for (let i = 0; i < count; i++) {
    const offset = offsetFn(count > 1 ? i / (count - 1) : 0);
    result.push([
      points[i][0] - normals[i][1] * offset,
      points[i][1] + normals[i][0] * offset,
    ]);
  }
  return result;
}

export function roundToTenth(num) {
  return Math.round(10 * num) / 10;
}

/**
 * 核心：手绘线条多边形路径生成器（闭合多边形）
 */
export function makeHandDrawnLine(points, options = {}) {
  const count = points.length;
  if (count < 2) return "";

  const width = options.width || 4;
  const seed = options.seed ?? 1;
  const taperIn = options.taperIn != null ? options.taperIn : 0.14;
  const taperOut = options.taperOut != null ? options.taperOut : 0.08;
  const startWidth = options.startWidth != null ? options.startWidth : 0.14;
  const endWidth = options.endWidth != null ? options.endWidth : 0.8;
  const belly = options.belly != null ? options.belly : 0.07;
  const wobble = options.wobble != null ? options.wobble : 0.14;
  const edge = options.edge != null ? options.edge : 0.055 * width + 0.35;
  const chisel = options.chisel != null ? options.chisel : 0.35;

  const normalRng1 = createSeededNormal(7 * seed + 1, Math.max(3, Math.round(count / 9)));
  const normalRng2 = createSeededNormal(13 * seed + 2, Math.max(4, Math.round(count / 5)));
  const normalRng3 = createSeededNormal(17 * seed + 3, Math.max(4, Math.round(count / 5)));
  const normals = getPolylineNormals(points);

  const leftOutline = [];
  const rightOutline = [];

  for (let i = 0; i < count; i++) {
    const progress = i / (count - 1);
    let factor = 1;
    let s;

    if (taperIn > 0 && progress < taperIn) {
      s = progress / taperIn;
      s = s * s * (3 - 2 * s);
      factor *= startWidth + (1 - startWidth) * s;
    }
    if (taperOut > 0 && progress > 1 - taperOut) {
      s = (1 - progress) / taperOut;
      s = s * s * (3 - 2 * s);
      factor *= endWidth + (1 - endWidth) * s;
    }

    factor *= (1 + belly * Math.sin(Math.PI * progress)) * (1 + wobble * normalRng1(progress));
    const halfWidth = Math.max(0.25, (width * factor) / 2);

    const normalX = -normals[i][1];
    const normalY = normals[i][0];

    leftOutline.push([
      points[i][0] + normalX * (halfWidth + edge * normalRng2(progress)),
      points[i][1] + normalY * (halfWidth + edge * normalRng2(progress)),
    ]);
    rightOutline.push([
      points[i][0] - normalX * (halfWidth + edge * normalRng3(progress)),
      points[i][1] - normalY * (halfWidth + edge * normalRng3(progress)),
    ]);
  }

  const chiselOffset = chisel * width;
  leftOutline[count - 1] = [
    leftOutline[count - 1][0] + normals[count - 1][0] * chiselOffset,
    leftOutline[count - 1][1] + normals[count - 1][1] * chiselOffset,
  ];
  rightOutline[0] = [
    rightOutline[0][0] - normals[0][0] * chiselOffset * 0.6,
    rightOutline[0][1] - normals[0][1] * chiselOffset * 0.6,
  ];

  let pathData = "M" + roundToTenth(leftOutline[0][0]) + " " + roundToTenth(leftOutline[0][1]);
  for (let i = 1; i < count; i++) {
    pathData += "L" + roundToTenth(leftOutline[i][0]) + " " + roundToTenth(leftOutline[i][1]);
  }
  for (let i = count - 1; i >= 0; i--) {
    pathData += "L" + roundToTenth(rightOutline[i][0]) + " " + roundToTenth(rightOutline[i][1]);
  }
  return pathData + "Z";
}

// 兼容别名导出
export const tH = NOISE_TABLE;
export const tD = fastNoise;
export const tY = smoothStep;
export const tU = darkenColor;
export const tK = resamplePolyline;
export const tX = getPolylineLength;
export const tJ = getPolylineNormals;
export const tQ = offsetPolyline;
