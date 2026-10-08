// 从 trinkets 的 mod jar 里抽出它内嵌的 cardinal-components 库（jar-in-jar），
// 放到工程之外的 ../durahud-build/libs/，供编译期的 modCompileOnly 使用。
//
// 为什么需要这一步：TrinketComponent 继承自 cardinal-components 的 ComponentV3，
// 而 javac 解析成员时必须能看到父类，所以这两个内嵌 jar 必须单独出现在编译类路径上。
//
// 用法：node tools/extract-cca.mjs [trinketsJar] [输出目录]
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const PROJECT_DIR = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const DEFAULT_JAR = process.env.TRINKETS_JAR || path.join(PROJECT_DIR, '..', 'libs', 'trinkets-3.7.2.jar');
const DEFAULT_OUT = path.join(PROJECT_DIR, '..', 'durahud-build', 'libs');

const jarPath = process.argv[2] || DEFAULT_JAR;
const outDir = process.argv[3] || DEFAULT_OUT;
const buf = fs.readFileSync(jarPath);
let eocd = buf.length - 22;
while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd--;
if (eocd < 0) throw new Error('不是 zip：' + jarPath);
const count = buf.readUInt16LE(eocd + 10);
let p = buf.readUInt32LE(eocd + 16);
const wanted = new Set();
for (let i = 0; i < count; i++) {
  const nameLength = buf.readUInt16LE(p + 28);
  const extraLength = buf.readUInt16LE(p + 30);
  const commentLength = buf.readUInt16LE(p + 32);
  const name = buf.toString('utf8', p + 46, p + 46 + nameLength);
  if (name.startsWith('META-INF/jars/') && name.endsWith('.jar')) {
    const method = buf.readUInt16LE(p + 10);
    const compressedSize = buf.readUInt32LE(p + 20);
    const localOffset = buf.readUInt32LE(p + 42);
    const localNameLength = buf.readUInt16LE(localOffset + 26);
    const localExtraLength = buf.readUInt16LE(localOffset + 28);
    const start = localOffset + 30 + localNameLength + localExtraLength;
    const raw = buf.subarray(start, start + compressedSize);
    wanted.add({ name: path.basename(name), data: method === 0 ? Buffer.from(raw) : zlib.inflateRawSync(raw) });
  }
  p += 46 + nameLength + extraLength + commentLength;
}
if (wanted.size === 0) throw new Error('这个 jar 里没有 META-INF/jars 内嵌库：' + jarPath);
fs.mkdirSync(outDir, { recursive: true });
for (const entry of wanted) {
  const file = path.join(outDir, entry.name);
  fs.writeFileSync(file, entry.data);
  console.log('写出 ' + file + '（' + entry.data.length + ' 字节）');
}
