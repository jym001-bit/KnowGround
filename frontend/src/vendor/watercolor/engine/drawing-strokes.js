import { escapeAttribute } from "./escape.js";
import {
  resamplePolyline,
  getPolylineLength,
  makeHandDrawnLine,
  offsetPolyline,
  darkenColor,
  tK,
  tX,
  tQ,
  tU,
} from "./drawing-noise.js";
import { createSeededNormal, createSeededRandom } from "./prng.js";

/**
 * 轻量级 SVG 杂色滤镜生成器
 */
export function makeLiteFilter(id, baseFrequency, seed, compositeK3) {
  return (
    '<filter id="' +
    escapeAttribute(id) +
    '" x="-8%" y="-8%" width="116%" height="116%" color-interpolation-filters="sRGB">' +
    '<feTurbulence type="fractalNoise" baseFrequency="' +
    baseFrequency +
    '" numOctaves="2" seed="' +
    seed +
    '" result="grain"/>' +
    '<feColorMatrix in="grain" type="luminanceToAlpha" result="ga"/>' +
    '<feComposite in="SourceGraphic" in2="ga" operator="arithmetic" k1="0" k2="1" k3="' +
    compositeK3 +
    '" k4="0"/>' +
    "</filter>"
  );
}

export function computeStrokeSamples(points, options) {
  return options && options.samples
    ? options.samples
    : Math.max(16, Math.min(160, Math.round(getPolylineLength(points) / 5)));
}

/**
 * 包装为正片叠底图层
 */
export function wrapBlendMultiply(svgContent, options) {
  return (
    '<g class="mk"' +
    (options && false === options.blend ? "" : ' style="mix-blend-mode:multiply"') +
    ">" +
    svgContent +
    "</g>"
  );
}

/**
 * 生成马克笔笔触路径（支持笔芯 core 与边缘刮痕 streaks）
 */
export function makeMarkerPath(points, options = {}) {
  const width = options.width || 4;
  const seed = options.seed ?? 1;
  const color = options.color || "#333";
  const sampled = resamplePolyline(points, computeStrokeSamples(points, options));
  const opacity = options.opacity != null ? options.opacity : 0.92;

  let pathSvg =
    '<path d="' +
    makeHandDrawnLine(sampled, {
      width,
      seed,
      taperIn: options.taperIn,
      taperOut: options.taperOut,
      startWidth: options.startWidth,
      endWidth: options.endWidth,
      belly: options.belly,
      wobble: options.wobble,
      edge: options.edge,
      chisel: options.chisel,
    }) +
    '" fill="' +
    escapeAttribute(color) +
    '" fill-opacity="' +
    opacity +
    '"/>';

  // 粗笔触生成内层深色墨芯（core）
  if (width >= 3.5 && false !== options.core) {
    const normalRng = createSeededNormal(5 * seed + 9, 5);
    const corePoints = offsetPolyline(sampled, (t) => normalRng(t) * width * 0.17);
    pathSvg +=
      '<path d="' +
      makeHandDrawnLine(corePoints, {
        width: 0.45 * width,
        seed: seed + 31,
        taperIn: 0.2,
        taperOut: 0.16,
        startWidth: 0.2,
        endWidth: 0.5,
        edge: 0.03 * width + 0.3,
        chisel: 0.25,
      }) +
      '" fill="' +
      escapeAttribute(darkenColor(color, 0.12)) +
      '" fill-opacity="0.30"/>';

    const innerPoints = offsetPolyline(sampled, () => 0.3 * width);
    pathSvg +=
      '<path d="' +
      makeHandDrawnLine(innerPoints, {
        width: 0.16 * width,
        seed: seed + 57,
        taperIn: 0.25,
        taperOut: 0.2,
        startWidth: 0.15,
        endWidth: 0.4,
        edge: 0.5,
        chisel: 0.15,
      }) +
      '" fill="' +
      escapeAttribute(darkenColor(color, 0.18)) +
      '" fill-opacity="0.22"/>';
  }

  // 超粗笔触生成纸张底色露白纹理（streaks）
  if (width >= 14 && false !== options.streaks) {
    const paperColor = options.paper || "#FAF9F5";
    [-0.22, 0.05, 0.26].forEach((offsetRatio, idx) => {
      const streakPoints = offsetPolyline(
        sampled,
        (t) => offsetRatio * width * (1 + 0.15 * Math.sin(5 * t + idx)),
      );
      pathSvg +=
        '<path d="' +
        makeHandDrawnLine(streakPoints, {
          width: width * (0.09 + 0.04 * idx),
          seed: seed + 71 + 13 * idx,
          taperIn: 0.3,
          taperOut: 0.25,
          startWidth: 0.2,
          endWidth: 0.3,
          edge: 0.6,
          chisel: 0.1,
        }) +
        '" fill="' +
        escapeAttribute(paperColor) +
        '" fill-opacity="' +
        (0.1 + (idx % 2) * 0.04) +
        '"/>';
    });
  }

  return pathSvg;
}

/**
 * 生成完整马克笔笔触 SVG 组合
 */
export function makeMarkerStroke(points, options = {}) {
  return wrapBlendMultiply(makeMarkerPath(points, options), options);
}

/**
 * 生成干画笔/断断续续手绘线条片段数组
 */
export function renderDryBrushSegments(points, options = {}) {
  if (points.length < 2) return [];
  const width = Math.max(.1, Number(options.width) || 4);
  const seed = options.seed ?? 1;
  const color = options.color || "#333";
  const dashLength = Math.max(.5, Number(options.dashLength) || 3.4 * width);
  const gap = Math.max(0, options.gap ?? 1.6 * width);
  const opacity = options.opacity != null ? options.opacity : 1;
  const sampled = resamplePolyline(points, Math.max(48, computeStrokeSamples(points, options)));
  const totalLen = getPolylineLength(sampled);
  const rng = createSeededRandom(101 * seed + 5);
  const neat = !!options.neat;
  const segments = [];

  let startDist = 0.3 * dashLength * rng();
  let prevEnd = 0;

  const buildSegment = (fromDist, toDist) => {
    const fromFrac = fromDist / totalLen;
    const toFrac = toDist / totalLen;
    const startIdx = Math.max(0, Math.floor(fromFrac * (sampled.length - 1)));
    const endIdx = Math.min(sampled.length - 1, Math.ceil(toFrac * (sampled.length - 1)));

    if (endIdx - startIdx < 1) return "";

    const subPoints = resamplePolyline(sampled.slice(startIdx, endIdx + 1), 8);
    const angle = (rng() - 0.5) * (neat ? 0.018 : 0.06);
    const cosA = Math.cos(angle);
    const sinA = Math.sin(angle);

    let centerX = 0;
    let centerY = 0;
    for (let i = 0; i < subPoints.length; i++) {
      centerX += subPoints[i][0];
      centerY += subPoints[i][1];
    }
    centerX /= subPoints.length;
    centerY /= subPoints.length;

    for (let i = 0; i < subPoints.length; i++) {
      const dx = subPoints[i][0] - centerX;
      const dy = subPoints[i][1] - centerY;
      subPoints[i] = [centerX + dx * cosA - dy * sinA, centerY + dx * sinA + dy * cosA];
    }

    const segWidth = width * (neat ? 0.94 + 0.12 * rng() : 0.85 + 0.35 * rng());
    let segPath =
      '<path d="' +
      makeHandDrawnLine(subPoints, {
        width: segWidth,
        seed: seed + Math.round(fromDist),
        taperIn: 0.18,
        taperOut: 0.18,
        startWidth: 0.55,
        endWidth: 0.5,
        belly: neat ? 0.02 : 0.05,
        wobble: neat ? 0.03 : 0.08,
        edge: neat ? 0.25 : 0.05 * segWidth + 0.3,
        chisel: neat ? 0.2 : 0.35,
      }) +
      '" fill="' +
      escapeAttribute(color) +
      '" fill-opacity="' +
      ((neat ? 0.9 + 0.08 * rng() : 0.82 + 0.16 * rng()) * opacity).toFixed(2) +
      '"/>';

    if (rng() < (neat ? 0.12 : 0.35)) {
      segPath +=
        '<path d="' +
        makeHandDrawnLine(subPoints, {
          width: 0.5 * segWidth,
          seed: seed + Math.round(fromDist) + 7,
          taperIn: 0.3,
          taperOut: 0.3,
          startWidth: 0.3,
          endWidth: 0.3,
          edge: 0.4,
          chisel: 0.2,
        }) +
        '" fill="' +
        escapeAttribute(darkenColor(color, 0.13)) +
        '" fill-opacity="' +
        (0.3 * opacity).toFixed(2) +
        '"/>';
    }
    return segPath;
  };

  while (startDist < totalLen - 2) {
    const endDist = Math.min(
      totalLen,
      startDist + dashLength * (neat ? 0.92 + 0.16 * rng() : 0.8 + 0.45 * rng()),
    );
    segments.push(buildSegment(startDist, endDist));
    prevEnd = endDist;
    startDist = endDist + gap * (neat ? 0.9 + 0.2 * rng() : 0.75 + 0.5 * rng());
  }

  if (options.landOnEnd && totalLen - prevEnd > 0.5 * dashLength) {
    segments.push(buildSegment(Math.max(prevEnd + 0.4 * gap, totalLen - dashLength), totalLen));
  }

  return segments;
}

/**
 * 连续干画笔笔触
 */
export function makeDryStroke(points, options = {}) {
  return renderDryBrushSegments(points, options).join("");
}

/**
 * 虚线/规则划线笔触（如网格尺线）
 */
export function makeRuleStroke(points, options = {}) {
  return wrapBlendMultiply(
    Array.isArray(points) ? makeDryStroke(points, options) : points,
    options,
  );
}

// 兼容别名
export const t6 = computeStrokeSamples;
export const t8 = wrapBlendMultiply;
export const at = renderDryBrushSegments;
