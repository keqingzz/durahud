// 无头预览工具：把 build/preview/layout.json 里的几何 + 原版 widgets.png / bars.png
// 合成成 PNG，用来在不开游戏的情况下肉眼检查布局（A1 的一部分）。
//
// 用法：gradle test（会写出 layout.json）之后执行  node tools/preview.mjs
//
// 限制：物品图标与文字用半透明占位块表示 —— 离屏渲染物品需要整个 Minecraft 运行时，
// 所以预览只保证<b>位置与外观（格子/高亮/耐久条）</b>与游戏一致。
//
// 输入：build/preview/layout.json（gradle test 写出）+ 原版 widgets.png / bars.png。
// 输出：build/preview/<case>.png，每个窗口尺寸一张，并把绝对路径逐行打到 stdout。
// 怎么跑：见上面的用法；layout.json 必须由测试写出，不要手动造（README 11.7）。
// 依赖：Node 内置模块 + 同目录的 png.mjs、assets.mjs；assets.mjs 需要本机 .minecraft 在位。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng, encodePng } from './png.mjs';
import { loadTexture } from './assets.mjs';

const TOOLS_DIR = path.dirname(fileURLToPath(import.meta.url));   // tools/：相对脚本定位，任意 cwd 都能跑
const PROJECT_DIR = path.dirname(TOOLS_DIR);                     // 工程根
const OUT_DIR = path.join(PROJECT_DIR, 'build', 'preview');      // 与 layout.json 同目录，产物一起被清理

// layout.json 不存在时这里会直接抛异常：比拿着 undefined 画出一张空白图好查得多。
const layout = JSON.parse(fs.readFileSync(path.join(OUT_DIR, 'layout.json'), 'utf8'));
// widgets.png 提供格子与高亮，bars.png 提供耐久条色带；都取原版资源，保证和游戏里看到的一致。
const widgets = decodePng(loadTexture('minecraft/textures/gui/widgets.png').buffer);
const bars = decodePng(loadTexture('minecraft/textures/gui/bars.png').buffer);

/** 新建一张不透明画布：深灰底 [24,24,24] 是为了让暗色与半透明的边缘也能看清。 */
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

/**
 * 把 img 的一段（u,v,w,h）缩放到 (dx,dy,dw,dh)，最近邻采样，做常规 alpha 混合。
 * 最近邻是刻意的：像素风 GUI 一旦做插值，1px 的边框就会糊掉，看不出错位。
 */
function blit(target, img, u, v, w, h, dx, dy, dw, dh) {
  for (let y = 0; y < dh; y++) {
    // Math.min(h - 1, ...) 是给放大准备的：dw/dh 大于源尺寸时，采样点不能越出源图。
    const sy = v + Math.min(h - 1, Math.floor((y * h) / dh));
    const ty = dy + y;
    if (ty < 0 || ty >= target.height) continue;    // 越出画布就丢弃：调用方给的几何可能压到边缘
    for (let x = 0; x < dw; x++) {
      const sx = u + Math.min(w - 1, Math.floor((x * w) / dw));
      const tx = dx + x;
      if (tx < 0 || tx >= target.width) continue;
      const src = (sy * img.width + sx) * 4;
      const alpha = img.data[src + 3] / 255;
      if (alpha === 0) continue;                    // 全透明像素跳过，免得把底下的背景擦掉
      const dst = (ty * target.width + tx) * 4;
      for (let c = 0; c < 3; c++) {
        target.data[dst + c] = Math.round(img.data[src + c] * alpha + target.data[dst + c] * (1 - alpha));
      }
      target.data[dst + 3] = 255;                   // 画布本身不透明，混合后一律补满 alpha
    }
  }
}

/**
 * 半透明占位块。物品图标与文字没法离屏渲染（那需要整个 Minecraft 运行时），
 * 所以预览只用一块淡色矩形表示「这里会有东西」，用来核对位置与尺寸。
 * 自动裁剪到画布内，允许调用方传越界甚至负的坐标。
 */
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

/**
 * 把目标条的第 x 列映射到 bars.png 的源列。三段切片与 Java 侧 Atlas.drawSliced 是同一套算法：
 * 左 capLeft 列与右 capRight 列原样保留，中间按比例拉伸 —— 这样圆角与端头不会被拉变形。
 */
function sourceU(x, w) {
  const capLeft = layout.capLeft;
  const capRight = layout.capRight;
  const middle = w - capLeft - capRight;
  if (middle <= 0) return x;                                  // 条太短、装不下两个端头：退化成直接取源列
  if (x < capLeft) return x;                                  // 左端头
  if (x >= w - capRight) return layout.barFullW - (w - x);    // 右端头：从满宽倒着数
  // 中间段：把 x 归一到 [0, middle)，再放大到「满宽去掉两个端头」的宽度。
  return capLeft + Math.floor(((x - capLeft) * (layout.barFullW - capLeft - capRight)) / middle);
}

/** 画一行条。bars.png 里每个色档占 barStep 高：先 barHeight 行是底条，紧接着的 barHeight 行是进度条。 */
function drawBarRow(target, x, y, w, colorIndex, progress) {
  if (w <= 0) return;                                          // 宽度为 0 时别白跑循环
  const baseV = colorIndex * layout.barStep + (progress ? layout.barHeight : 0);
  for (let i = 0; i < w; i++) {
    blit(target, bars, sourceU(i, w), baseV, 1, layout.barHeight, x + i, y, 1, layout.barHeight);   // 逐列取源列
  }
}

const written = [];
for (const item of layout.cases) {               // 每个 case = 一种窗口尺寸（对应一次窗口缩放）
  const [screenW, screenH] = item.screen;
  const [originX, originY] = item.origin;        // HUD 左上角在屏幕上的位置
  const scale = item.scale;
  const target = canvas(screenW, screenH, [24, 24, 24]);
  // 下面这些是布局常量，来自 Java 侧 HudLayout，与窗口尺寸无关
  const [cellW, cellH] = layout.cellSize;
  const [cellU, cellV] = layout.cellUv;
  const [iconDx, iconDy, iconSize] = layout.iconOffset;
  const [barOffsetX, barOffsetY] = item.barOffset;
  // 三套坐标（README 8.6）：布局局部坐标 -> 屏幕像素统一在这里换算。
  // 一律先乘 scale 再取整，并且只走这两个函数，免得与渲染层的取整口径不一致而差 1px。
  const toScreenX = (value) => Math.round(originX + value * scale);
  const toScreenY = (value) => Math.round(originY + value * scale);
  const scaled = (value) => Math.round(value * scale);   // 纯尺寸换算：不带 origin 偏移

  for (let i = 0; i < item.cells.length; i++) {   // cells 与 entries 一一对应（Java 侧同一个循环写出来的）
    const [cellX, cellY] = item.cells[i];
    blit(target, widgets, cellU, cellV, cellW, cellH,
      toScreenX(cellX), toScreenY(cellY), scaled(cellW), scaled(cellH));
    placeholder(target, toScreenX(cellX + iconDx), toScreenY(cellY + iconDy),
      scaled(iconSize), scaled(iconSize), [200, 200, 210], 0.35);   // 物品图标占位
    const entry = item.entries[i];
    drawBar(target, toScreenX(cellX + barOffsetX), toScreenY(cellY + barOffsetY),
      scaled(item.barLength), entry.barColorIndex, Math.round(scaled(item.barLength) * entry.ratio));
    if (entry.critical) {
      // 濒危高亮：用原版选中框贴图居中盖在格子上（尺寸同样先缩放再落位）。
      blit(target, widgets, layout.selectUv[0], layout.selectUv[1], layout.selectUv[2], layout.selectUv[3],
        toScreenX(cellX + (cellW - layout.selectUv[2]) / 2), toScreenY(cellY + (cellH - layout.selectUv[3]) / 2),
        scaled(layout.selectUv[2]), scaled(layout.selectUv[3]));
    }
    // Java 侧「没有文字」时写的是字符串 'null'（不是 JSON null），所以这里比的是字符串。
    if (item.textSide !== 'null') {
      // 高亮块 = 布局里给文字预留的整块：VALUE 模式是两行，高度由 HudLayout.textHeight() 给出
      // （旧版生成的 layout.json 没有这个字段，退回一行的 9）。
      const textH = item.textHeight ?? 9;
      // textColor 在 layout.json 里是打包的 0xRRGGBB，这里拆成三个通道给占位块用。
      // 折成两行只是为了不超过 120 列，表达式本身没动。
      placeholder(target, toScreenX(cellX + item.textOffsetX), toScreenY(cellY + item.textOffsetY),
        scaled(item.textWidth), scaled(textH),
        [entry.textColor >> 16 & 0xff, entry.textColor >> 8 & 0xff, entry.textColor & 0xff], 0.30);
    }
  }
  const file = path.join(OUT_DIR, item.name + '.png');
  fs.writeFileSync(file, encodePng(target.width, target.height, target.data));
  written.push(item.name + ' -> ' + file);   // 先攒着，等全部写完再打印，半路失败就不会只报一半
}

for (const line of written) console.log(line);   // 每条一个绝对路径，便于直接点开看图
