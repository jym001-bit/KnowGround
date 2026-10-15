/**
 * 手绘水彩视觉系统色板与设计 Token
 */

export const MODEST_PALETTE = ["#6A9BCC", "#8BADCF", "#A8C4DE", "#C4D9EA"];
export const SUBSTANTIAL_PALETTE = ["#788C5D", "#8AA16D", "#A2B48B", "#B3C1A0"];
export const EXTREME_PALETTE = ["#C46686", "#CC7D98", "#D89EB0", "#E2B6C4"];

export function pickPaletteShade(palette, ratio) {
  const index = ratio < 0.42 ? 0 : ratio < 0.72 ? 1 : ratio < 0.92 ? 2 : 3;
  return palette[Math.min(index, palette.length - 1)] ?? "#6A9BCC";
}

export const SCENARIO_PALETTES = {
  slow: MODEST_PALETTE,
  central: SUBSTANTIAL_PALETTE,
  fast: EXTREME_PALETTE,
};

// 语义墨水色
export const NEUTRAL_INK = "#BDBAAE";
export const AUGMENTATION_INK = "#918BD6";
export const AUTOMATION_INK = "#6A9BCC";
export const NEW_TASK_INK = "#F0AC54";
export const BASELINE_INK = "#D97757";

// 图表主题颜色
export const CHART_THEME = {
  paper: "#FAF9F5",
  rule: "#87867F",
  text: "#141413",
  history: "#9C9A92",
  baseline: BASELINE_INK,
};
