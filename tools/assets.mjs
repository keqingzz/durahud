// 定位并读取原版贴图（widgets.png / bars.png）。
//
// 顺序：先看启用的资源包（versions/<ver>/resourcepacks 与 .minecraft/resourcepacks）里有没有
// 覆盖 assets/minecraft/textures/... 的同名文件 —— 有的话游戏里看到的就是它；
// 否则回落到官方资源（按 assets/indexes/<id>.json 的哈希从 assets/objects 取）。
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

export const MINECRAFT_DIR = process.env.MINECRAFT_DIR || path.resolve(process.cwd(), '.minecraft');
export const VERSION_ID = process.env.MINECRAFT_VERSION_ID || '1.20.1-Fabric 0.17.3';
export const VERSION_DIR = path.join(MINECRAFT_DIR, 'versions', VERSION_ID);

/** 读 asset index（版本 json 里 assetIndex.id 对应的文件）。 */
function assetIndex() {
  const versionJson = JSON.parse(fs.readFileSync(path.join(VERSION_DIR, VERSION_ID + '.json'), 'utf8'));
  const id = versionJson.assetIndex.id;
  return JSON.parse(fs.readFileSync(path.join(MINECRAFT_DIR, 'assets', 'indexes', id + '.json'), 'utf8'));
}

/** 极简 zip 读取：只解析中央目录，按需解压单个条目。 */
function openZip(file) {
  const buf = fs.readFileSync(file);
  let eocd = buf.length - 22;
  while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd--;
  if (eocd < 0) throw new Error('不是 zip：' + file);
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const entries = new Map();
  for (let i = 0; i < count; i++) {
    const nameLength = buf.readUInt16LE(p + 28);
    const extraLength = buf.readUInt16LE(p + 30);
    const commentLength = buf.readUInt16LE(p + 32);
    const method = buf.readUInt16LE(p + 10);
    const compressedSize = buf.readUInt32LE(p + 20);
    const localOffset = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLength);
    entries.set(name, { method, compressedSize, localOffset });
    p += 46 + nameLength + extraLength + commentLength;
  }
  return {
    names: () => [...entries.keys()],
    read(name) {
      const entry = entries.get(name);
      if (!entry) return null;
      const nameLength = buf.readUInt16LE(entry.localOffset + 26);
      const extraLength = buf.readUInt16LE(entry.localOffset + 28);
      const start = entry.localOffset + 30 + nameLength + extraLength;
      const data = buf.subarray(start, start + entry.compressedSize);
      return entry.method === 0 ? Buffer.from(data) : zlib.inflateRawSync(data);
    },
  };
}

function resourcepackDirs() {
  return [path.join(VERSION_DIR, 'resourcepacks'), path.join(MINECRAFT_DIR, 'resourcepacks')]
    .filter((dir) => fs.existsSync(dir));
}

/**
 * 取一张贴图。relPath 形如 'minecraft/textures/gui/widgets.png'。
 * @returns {{ buffer: Buffer, source: string }}
 */
export function loadTexture(relPath) {
  const entryName = 'assets/' + relPath;
  for (const dir of resourcepackDirs()) {
    for (const file of fs.readdirSync(dir)) {
      if (!file.toLowerCase().endsWith('.zip')) continue;
      try {
        const zip = openZip(path.join(dir, file));
        if (zip.names().includes(entryName)) {
          return { buffer: zip.read(entryName), source: '资源包 ' + file + ' 覆盖了 ' + entryName };
        }
      } catch (error) {
        // 单个资源包读不动不影响整体：继续看下一个
      }
    }
  }
  const index = assetIndex();
  const object = index.objects[relPath];
  if (!object) throw new Error('资源索引里没有 ' + relPath);
  const hash = object.hash;
  const file = path.join(MINECRAFT_DIR, 'assets', 'objects', hash.slice(0, 2), hash);
  return { buffer: fs.readFileSync(file), source: '原版资源 ' + hash.slice(0, 12) };
}
