// 定位并读取原版贴图（widgets.png / bars.png）。
//
// 顺序：先看启用的资源包（versions/<ver>/resourcepacks 与 .minecraft/resourcepacks）里有没有
// 覆盖 assets/minecraft/textures/... 的同名文件 —— 有的话游戏里看到的就是它；
// 否则回落到官方资源（按 assets/indexes/<id>.json 的哈希从 assets/objects 取）。
//
// 输入：relPath，形如 'minecraft/textures/gui/widgets.png'（不带 assets/ 前缀）。
// 输出：{ buffer, source }；source 是给人看的来源说明（哪个包覆盖了 / 哪份官方资源）。
// 怎么跑：不单独跑，被 preview.mjs import；本机 .minecraft 必须在位（见下面两个常量）。
// 依赖：只用 Node 内置模块 + 本机游戏文件；不需要构建、不需要先跑测试。刻意不引第三方 zip 库（见 openZip）。
// 注意：不要把这个资源包的文件名原样抄进代码或文档 —— 本机那个包名带颜色控制字符（README 13.9）。
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

// 路径由环境变量给出（MINECRAFT_DIR / MINECRAFT_VERSION_ID）。VERSION_DIR 用 path.join 拼而不是写整串：
// 版本目录名里带空格，整串硬编码容易少一个空格而报「找不到文件」。
export const MINECRAFT_DIR = process.env.MINECRAFT_DIR;
export const VERSION_DIR = path.join(MINECRAFT_DIR, 'versions', process.env.MINECRAFT_VERSION_ID);

/**
 * 读 asset index。必须经版本 json 的 assetIndex.id 绕一次：索引文件名不一定等于版本名
 * （官方版本里两者常常不同），直接拼 '1.20.1.json' 迟早会踩空。
 */
function assetIndex() {
  const versionJson = JSON.parse(fs.readFileSync(path.join(VERSION_DIR, process.env.MINECRAFT_VERSION_ID + '.json'), 'utf8'));
  const id = versionJson.assetIndex.id;   // 索引 id 才是 assets/indexes/<id>.json 的文件名
  return JSON.parse(fs.readFileSync(path.join(MINECRAFT_DIR, 'assets', 'indexes', id + '.json'), 'utf8'));
}

/**
 * 极简 zip 读取：只解析中央目录，按需解压单个条目。
 * 不用第三方 zip 库是刻意的：tools 的定位是「拷贝工程就能跑」，不引入 npm 依赖（README 8.4）。
 */
function openZip(file) {
  const buf = fs.readFileSync(file);
  let eocd = buf.length - 22;                                             // EOCD 记录最短 22 字节
  while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd--;      // 从尾部往回找 'PK'
  if (eocd < 0) throw new Error('不是 zip：' + file);                     // 往回找而不是直接定位末尾：zip 允许带注释
  const count = buf.readUInt16LE(eocd + 10);                             // 中央目录条目数
  let p = buf.readUInt32LE(eocd + 16);                                   // 中央目录起始偏移
  const entries = new Map();
  for (let i = 0; i < count; i++) {
    const nameLength = buf.readUInt16LE(p + 28);      // 中央目录条目固定 46 字节头，以下偏移都相对它
    const extraLength = buf.readUInt16LE(p + 30);
    const commentLength = buf.readUInt16LE(p + 32);
    const method = buf.readUInt16LE(p + 10);          // 0 = stored（不压缩），8 = deflate
    const compressedSize = buf.readUInt32LE(p + 20);  // 压缩后的字节数
    const localOffset = buf.readUInt32LE(p + 42);     // 本地头位置：真正取数据时要用
    const name = buf.toString('utf8', p + 46, p + 46 + nameLength);
    entries.set(name, { method, compressedSize, localOffset });
    p += 46 + nameLength + extraLength + commentLength;   // 跳到下一个中央目录条目
  }
  return {
    names: () => [...entries.keys()],
    read(name) {
      const entry = entries.get(name);
      if (!entry) return null;                                            // 没这个条目，由调用者决定怎么兜底
      const nameLength = buf.readUInt16LE(entry.localOffset + 26);         // 本地头的 name / extra 长度可能与中央目录不同，
      const extraLength = buf.readUInt16LE(entry.localOffset + 28);        // 所以数据起点必须按本地头重算（30 = 本地头固定部分）
      const start = entry.localOffset + 30 + nameLength + extraLength;
      const data = buf.subarray(start, start + entry.compressedSize);
      // 8 = 裸 deflate（没有 zlib 头，所以用 inflateRaw，不能用 inflate）；0 = stored，直接拷一份。
      return entry.method === 0 ? Buffer.from(data) : zlib.inflateRawSync(data);
    },
  };
}

// 资源包可能放在两处：版本目录下的（启动器整合包默认）与全局 .minecraft 下的。
// 两处都扫，顺序不代表优先级 —— 谁先命中取决于「哪个包里真的有这个文件」，与游戏内一致。
function resourcepackDirs() {
  return [path.join(VERSION_DIR, 'resourcepacks'), path.join(MINECRAFT_DIR, 'resourcepacks')]
    .filter((dir) => fs.existsSync(dir));
}

/**
 * 取一张贴图。relPath 形如 'minecraft/textures/gui/widgets.png'。
 * @returns {{ buffer: Buffer, source: string }}
 */
export function loadTexture(relPath) {
  const entryName = 'assets/' + relPath;   // zip 内的路径带 assets/ 前缀，而资源索引的键不带
  for (const dir of resourcepackDirs()) {
    for (const file of fs.readdirSync(dir)) {
      if (!file.toLowerCase().endsWith('.zip')) continue;   // 只认 zip 形式的资源包；本机这个包就是 zip。toLowerCase 防 .ZIP
      try {
        const zip = openZip(path.join(dir, file));
        if (zip.names().includes(entryName)) {
          return { buffer: zip.read(entryName), source: '资源包 ' + file + ' 覆盖了 ' + entryName };
        }
      } catch (error) {
        // 单个资源包读不动不影响整体：继续看下一个（坏 zip 不能挡住后面的官方资源兜底）
      }
    }
  }
  const index = assetIndex();
  const object = index.objects[relPath];       // 索引的键就是 relPath（不带 assets/）
  if (!object) throw new Error('资源索引里没有 ' + relPath);
  const hash = object.hash;
  const file = path.join(MINECRAFT_DIR, 'assets', 'objects', hash.slice(0, 2), hash);   // 官方对象按哈希前两位分桶，文件名就是哈希
  return { buffer: fs.readFileSync(file), source: '原版资源 ' + hash.slice(0, 12) };   // source 只取哈希前 12 位：日志短一点就够认
}
