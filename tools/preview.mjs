// 无头预览工具：把 build/preview/layout.json 里的几何 + 原版 widgets.png / bars.png
// 合成成 PNG，用来在不开游戏的情况下肉眼检查布局（A1 的一部分）。
//
// 用法：gradle test（会写出 layout.json）之后执行  node tools/preview.mjs
//
// 限制：物品图标与文字用半透明占位块表示 —— 离屏渲染物品需要整个 Minecraft 运行时，
// 所以预览只保证<b>位置与外观（格子/高亮/耐久条）</b>与游戏一致。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng, encodePng } from './png.mjs';
import { loadTexture } from './assets.mjs';

const TOOLS_DIR = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_DIR = path.dirname(TOOLS_DIR);
const OUT_DIR = path.join(PROJECT_DIR, 'build', 'preview');

const layout = JSON.parse(fs.readFileSync(path.join(OUT_DIR, 'layout.json'), 'utf8'));
const widgets = decodePng(loadTexture('minecraft/textures/gui/widgets.png').buffer);
const bars = decodePng(loadTexture('minecraft/textures/gui/bars.png').buffer);

function canvas(width, height, background) {
  const data = Buffer.alloc(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    data[i * 4] = background[0];
    data[i * 4 + 1] = background[1];
    data[i * 4 + 2] = background[2];
    data[i * 4 + 3] = 255;
  }
  return { width, height, data };
}

/** 把 img 的一段（u,v,w,h）缩放到 (dx,dy,dw,dh)，最近邻采样，做常规 alpha 混合。 */
function blit(target, img, u, v, w, h, dx, dy, dw, dh) {
  for (let y = 0; y < dh; y++) {
    const sy = v + Math.min(h - 1, Math.floor((y * h) / dh));
    const ty = dy + y;
    if (ty < 0 || ty >= target.height) continue;
    for (let x = 0; x < dw; x++) {
      const sx = u + Math.min(w - 1, Math.floor((x * w) / dw));
      const tx = dx + x;
      if (tx < 0 || tx >= target.width) continue;
      const src = (sy * img.width + sx) * 4;
      const alpha = img.data[src + 3] / 255;
      if (alpha === 0) continue;
      const dst = (ty * target.width + tx) * 4;
      for (let c = 0; c < 3; c++) {
        target.data[dst + c] = Math.round(img.data[src + c] * alpha + target.data[dst + c] * (1 - alpha));
      }
      target.data[dst + 3] = 255;
    }
  }
}

function placeholder(target, x, y, w, h, rgb, alpha) {
  for (let ty = Math.max(0, y); ty < Math.min(target.height, y + h); ty++) {
    for (let tx = Math.max(0, x); tx < Math.min(target.width, x + w); tx++) {
      const dst = (ty * target.width + tx) * 4;
      for (let c = 0; c < 3; c++) {
        target.data[dst + c] = Math.round(rgb[c] * alpha + target.data[dst + c] * (1 - alpha));
      }
    }
  }
}

/**
 * 耐久条：先画底条（progress=false 的那 5px 行），再按比例画进度条。
 * 进度条沿用游戏里的非对称三段切片：左端 capLeft 像素、右端 capRight 像素保持原样，中间拉伸。
 */
function drawBar(target, x, y, w, colorIndex, progressPixels) {
  const h = layout.barHeight;
  drawBarRow(target, x, y, w, colorIndex, false);
  if (progressPixels > 0) drawBarRow(target, x, y, progressPixels, colorIndex, true);
}

function sourceU(x, w) {
  const capLeft = layout.capLeft;
  const capRight = layout.capRight;
  const middle = w - capLeft - capRight;
  if (middle <= 0) return x;
  if (x < capLeft) return x;
  if (x >= w - capRight) return layout.barFullW - (w - x);
  return capLeft + Math.floor(((x - capLeft) * (layout.barFullW - capLeft - capRight)) / middle);
}

function drawBarRow(target, x, y, w, colorIndex, progress) {
  if (w <= 0) return;
  const baseV = colorIndex * layout.barStep + (progress ? layout.barHeight : 0);
  for (let i = 0; i < w; i++) {
    blit(target, bars, sourceU(i, w), baseV, 1, layout.barHeight, x + i, y, 1, layout.barHeight);
  }
}

const written = [];
for (const item of layout.cases) {
  const [screenW, screenH] = item.screen;
  const [originX, originY] = item.origin;
  const scale = item.scale;
  const target = canvas(screenW, screenH, [24, 24, 24]);
  const [cellW, cellH] = layout.cellSize;
  const [cellU, cellV] = layout.cellUv;
  const [iconDx, iconDy, iconSize] = layout.iconOffset;
  const [barOffsetX, barOffsetY] = item.barOffset;
  const toScreenX = (value) => Math.round(originX + value * scale);
  const toScreenY = (value) => Math.round(originY + value * scale);
  const scaled = (value) => Math.round(value * scale);

  for (let i = 0; i < item.cells.length; i++) {
    const [cellX, cellY] = item.cells[i];
    blit(target, widgets, cellU, cellV, cellW, cellH,
      toScreenX(cellX), toScreenY(cellY), scaled(cellW), scaled(cellH));
    placeholder(target, toScreenX(cellX + iconDx), toScreenY(cellY + iconDy),
      scaled(iconSize), scaled(iconSize), [200, 200, 210], 0.35);
    const entry = item.entries[i];
    drawBar(target, toScreenX(cellX + barOffsetX), toScreenY(cellY + barOffsetY),
      scaled(item.barLength), entry.barColorIndex, Math.round(scaled(item.barLength) * entry.ratio));
    if (entry.critical) {
      blit(target, widgets, layout.selectUv[0], layout.selectUv[1], layout.selectUv[2], layout.selectUv[3],
        toScreenX(cellX + (cellW - layout.selectUv[2]) / 2), toScreenY(cellY + (cellH - layout.selectUv[3]) / 2),
        scaled(layout.selectUv[2]), scaled(layout.selectUv[3]));
    }
    if (item.textSide !== 'null') {
      // 高亮块 = 布局里给文字预留的整块：VALUE 模式是两行，高度由 HudLayout.textHeight() 给出
      // （旧版生成的 layout.json 没有这个字段，退回一行的 9）。
      const textH = item.textHeight ?? 9;
      placeholder(target, toScreenX(cellX + item.textOffsetX), toScreenY(cellY + item.textOffsetY),
        scaled(item.textWidth), scaled(textH), [entry.textColor >> 16 & 0xff, entry.textColor >> 8 & 0xff, entry.textColor & 0xff], 0.30);
    }
  }
  const file = path.join(OUT_DIR, item.name + '.png');
  fs.writeFileSync(file, encodePng(target.width, target.height, target.data));
  written.push(item.name + ' -> ' + file);
}

for (const line of written) console.log(line);
