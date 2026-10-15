import { makeMarkerStroke, makeMarkerPath } from "./drawing-strokes.js";
import { createSeededRandom } from "./prng.js";
import { darkenColor, hashNoise } from "./drawing-noise.js";
import { NEUTRAL_INK, AUGMENTATION_INK, AUTOMATION_INK } from "../tokens/palette.js";

export const TASK_CELL_SIZE = 52;

export function deriveSeed(seed) {
  return 7 * seed + 3;
}

/**
 * 手绘矩形/方块水彩铺底与风化边缘生成核心
 */
export function renderWatercolorRect(x, y, width, height, options = {}, colorFn = () => options.color ?? "#333") {
  if (![x, y, width, height].every(Number.isFinite) || width <= 0 || height <= 0) return { passes: [], weather: "" };
  const seed = options.seed ?? 1;
  const isVertical = "v" === options.direction;
  const span = isVertical ? width : height;
  const length = isVertical ? height : width;

  const rawWidth = options.strokeWidth ?? Math.min(24, Math.max(10, span / 3));
  const strokeWidth = Math.max(1, options.finePass ? rawWidth : Math.max(rawWidth, 10));
  const pointFn = (progress, offset) =>
    isVertical ? [x + offset, y + progress] : [x + progress, y + offset];
  const passCount = Math.max(1, Math.ceil(span / (0.72 * strokeWidth)));

  const baseColor = options.color ?? "#333";
  const endScatter = options.endScatter ?? 1;
  const rng = createSeededRandom(77 * seed + 3);
  const passes = [];

  for (let i = 0; i < passCount; i++) {
    const offset =
      (1 === passCount ? span / 2 : 0.45 * strokeWidth + (i * (span - 0.9 * strokeWidth)) / (passCount - 1)) +
      (rng() - 0.5) * strokeWidth * 0.08;
    const startX = (rng() - 0.5) * length * 0.02 * endScatter;
    const endX = length + (rng() - 0.5) * length * 0.02 * endScatter;
    const jitter = (rng() - 0.5) * strokeWidth * 0.12;

    const ptStart = pointFn(startX, offset - jitter);
    const ptMid = pointFn((startX + endX) / 2, offset + (rng() - 0.5) * strokeWidth * 0.08);
    const ptEnd = pointFn(endX, offset + jitter);

    passes.push(
      makeMarkerPath([ptStart, ptMid, ptEnd], {
        width: 1.18 * strokeWidth,
        seed: seed + 17 * i,
        color: colorFn(i, passCount),
        opacity: 1,
        taperIn: 0.025,
        taperOut: 0.02,
        startWidth: 0.88,
        endWidth: 0.88,
        belly: 0.015,
        wobble: 0.05,
        edge: 0.035 * strokeWidth + 0.4,
        chisel: 0.12,
        core: false,
        streaks: false,
      }),
    );
  }

  let weatherSvg = "";

  // 压力飞白与湿润感（pressure）
  if (false !== options.pressure && span > 26 && length > 40) {
    const pressurePasses = Math.max(2, Math.round(0.8 * passCount));
    for (let i = 0; i < pressurePasses; i++) {
      const pOffset = span * (0.1 + 0.8 * rng());
      const pWidth = strokeWidth * (1.2 + 0.8 * rng());
      const isDark = 0.65 > rng();
      const pColor = isDark ? darkenColor(baseColor, 0.05 + 0.06 * rng()) : (options.paper ?? "#FAF9F5");
      const pOpacity = isDark ? 0.09 + 0.05 * rng() : 0.07 + 0.04 * rng();
      const pJitter = (rng() - 0.5) * pWidth * 0.25;
      const pts = [
        pointFn(0.02 * length, pOffset - pJitter),
        pointFn(0.5 * length, pOffset + (rng() - 0.5) * pWidth * 0.15),
        pointFn(0.98 * length, pOffset + pJitter),
      ];

      [
        [1.6, 0.55],
        [0.85, 1],
      ].forEach(([scaleW, scaleO], idx) => {
        weatherSvg += makeMarkerPath(pts, {
          width: pWidth * scaleW,
          seed: seed + 51 + 19 * i + 7 * idx,
          color: pColor,
          opacity: pOpacity * scaleO,
          taperIn: 0.05,
          taperOut: 0.05,
          startWidth: 0.75,
          endWidth: 0.75,
          belly: 0.02,
          wobble: 0.06,
          edge: 0.22 * pWidth,
          chisel: 0.1,
          core: false,
          streaks: false,
        });
      });
    }
  }

  // 斑驳杂质（mottle）
  if (false !== options.mottle && span > 26 && length > 40) {
    const mottleCount = 2 + Math.floor(2 * rng());
    for (let i = 0; i < mottleCount; i++) {
      const centerX = x + width * (0.18 + 0.64 * rng());
      const centerY = y + height * (0.18 + 0.64 * rng());
      const angle = rng() * Math.PI;
      const mSpan = Math.min(length, span) * (0.35 + 0.5 * rng());
      const cosA = (Math.cos(angle) * mSpan) / 2;
      const sinA = (Math.sin(angle) * mSpan) / 2;
      weatherSvg += makeMarkerPath(
        [
          [centerX - cosA, centerY - sinA],
          [centerX + (rng() - 0.5) * mSpan * 0.2, centerY + (rng() - 0.5) * mSpan * 0.2],
          [centerX + cosA, centerY + sinA],
        ],
        {
          width: strokeWidth * (0.9 + 0.7 * rng()),
          seed: seed + 91 + 7 * i,
          color: darkenColor(baseColor, 0.08),
          opacity: 0.05 + 0.03 * rng(),
          taperIn: 0.25,
          taperOut: 0.25,
          startWidth: 0.35,
          endWidth: 0.35,
          belly: 0.1,
          wobble: 0.1,
          edge: 2,
          chisel: 0.1,
          core: false,
          streaks: false,
        },
      );
    }
  }

  // 水彩干燥边缘收缩框（rim）
  if (false !== options.rim && span > 18 && length > 18) {
    const rimWidth = Math.max(3, 0.16 * strokeWidth);
    const rimMargin = 0.55 * rimWidth + 1;
    const rimColor = darkenColor(baseColor, 0.13);
    const addRimSide = (ptA, ptB, sideIdx) =>
      makeMarkerPath([ptA, [(ptA[0] + ptB[0]) / 2, (ptA[1] + ptB[1]) / 2], ptB], {
        width: rimWidth,
        seed: seed + 171 + 13 * sideIdx,
        color: rimColor,
        opacity: 0.16,
        taperIn: 0.08,
        taperOut: 0.08,
        startWidth: 0.5,
        endWidth: 0.5,
        belly: 0,
        wobble: 0.07,
        edge: 0.5,
        chisel: 0.1,
        core: false,
        streaks: false,
      });

    weatherSvg +=
      addRimSide([x + rimMargin, y + rimMargin], [x + width - rimMargin, y + rimMargin], 0) +
      addRimSide([x + width - rimMargin, y + rimMargin], [x + width - rimMargin, y + height - rimMargin], 1) +
      addRimSide([x + width - rimMargin, y + height - rimMargin], [x + rimMargin, y + height - rimMargin], 2) +
      addRimSide([x + rimMargin, y + height - rimMargin], [x + rimMargin, y + rimMargin], 3);
  }

  return {
    passes,
    weather: weatherSvg,
  };
}

export function renderWatercolorPasses(x, y, width, height, colorFn, options = {}) {
  return renderWatercolorRect(x, y, width, height, options, colorFn).passes;
}

export function renderWatercolorWeather(x, y, width, height, options = {}) {
  return renderWatercolorRect(x, y, width, height, options).weather;
}

export function renderCompleteWatercolorBox(x, y, width, height, options = {}) {
  const opacity = options.opacity ?? 0.94;
  const { passes, weather } = renderWatercolorRect(x, y, width, height, options);
  return (
    '<g class="mk" style="mix-blend-mode:multiply"><g opacity="' +
    opacity +
    '">' +
    passes.join("") +
    "</g>" +
    weather +
    "</g>"
  );
}

/**
 * 生成单张手绘水彩小方块（可用于任务/格子组件）
 */
export function makeTaskSquare(index, size = TASK_CELL_SIZE, color = "currentColor") {
  const seed = deriveSeed(index);
  return {
    passes: renderWatercolorPasses(0, 0, size, size, () => color, {
      seed,
      color,
      paper: "#FAF9F5",
    }),
    weather: renderWatercolorWeather(0, 0, size, size, {
      color,
      seed,
      paper: "#FAF9F5",
    }),
  };
}

// 兼容别名
export const af = deriveSeed;
export const ag = renderWatercolorPasses;
export const ax = renderWatercolorWeather;
export const a_ = renderWatercolorRect;
export const aw = renderCompleteWatercolorBox;
