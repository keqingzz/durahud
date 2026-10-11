// 从整合包里的 trinkets jar 抽出它内嵌的 cardinal-components 库（jar-in-jar），
// 放到工程之外的 ../durahud-build/libs/，供编译期的 modCompileOnly 使用。
//
// 为什么需要这一步：TrinketComponent 继承自 cardinal-components 的 ComponentV3，
// 而 javac 解析成员时必须能看到父类，所以这两个内嵌 jar 必须单独出现在编译类路径上。
//
// 用法：node tools/extract-cca.mjs [trinketsJar] [输出目录]
//
// 输入：整合包里的 trinkets jar（默认就是下面的 DEFAULT_JAR，可用第 1 个参数覆盖）。
// 输出：把它内部 META-INF/jars/*.jar 逐个解到输出目录（默认 ../durahud-build/libs，可用第 2 个参数覆盖），
//       文件名保持内嵌 jar 的原名 —— gradle.properties 的 modCompileOnly 就是按这些名字引用的。
// 依赖：只用 Node 内置模块；不需要构建、不需要先跑测试；
//       但走默认路径时要求整合包与 mods/trinkets-*.jar 在位（README 2.5 / 11.7）。
//       换 MC 或 Trinkets 版本后，要么更新 DEFAULT_JAR，要么命令行传参。
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

// 从脚本自身位置反推工程根（tools/ 的上一级），所以在任意工作目录下调用都行。
const PROJECT_DIR = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
// 默认指向本整合包里唯一那份 trinkets jar：取值方式不要改（README 2.5），modCompileOnly 就靠它。
// 折成两行只是为了不超过 120 列，path.join 的实参序列没变。
const DEFAULT_JAR = process.env.TRINKETS_JAR
  || path.join(PROJECT_DIR, '..', 'libs', 'trinkets-3.7.2.jar');
// 输出刻意放在工程之外：抽出来的库是编译期临时依赖，不属于源码仓库，重建时整个 durahud-build 删掉即可。
const DEFAULT_OUT = path.join(PROJECT_DIR, '..', 'durahud-build', 'libs');

const jarPath = process.argv[2] || DEFAULT_JAR;
const outDir = process.argv[3] || DEFAULT_OUT;
const buf = fs.readFileSync(jarPath);
let eocd = buf.length - 22;                                          // EOCD 记录固定 22 字节
while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd--;   // 从尾部回扫 'PK\x05\x06'（zip 尾部可能带注释）
if (eocd < 0) throw new Error('不是 zip：' + jarPath);               // 扫不到就不是 zip，早点报错
const count = buf.readUInt16LE(eocd + 10);                           // 中央目录条目数
let p = buf.readUInt32LE(eocd + 16);                                 // 中央目录起始偏移，下面顺序读
const wanted = new Set();   // Set 顺便去重：同一个内嵌库只导一次；收尾用 size 判空决定是否报错
for (let i = 0; i < count; i++) {
  const nameLength = buf.readUInt16LE(p + 28);      // 中央目录条目固定 46 字节头，以下偏移都相对它
  const extraLength = buf.readUInt16LE(p + 30);
  const commentLength = buf.readUInt16LE(p + 32);
  const name = buf.toString('utf8', p + 46, p + 46 + nameLength);
  // Fabric 的 jar-in-jar 约定：内嵌库放 META-INF/jars/ 下、且自身就是个 jar。别的条目一概不管。
  if (name.startsWith('META-INF/jars/') && name.endsWith('.jar')) {
    const method = buf.readUInt16LE(p + 10);                          // 0 = stored（不压缩），8 = deflate
    const compressedSize = buf.readUInt32LE(p + 20);
    const localOffset = buf.readUInt32LE(p + 42);
    const localNameLength = buf.readUInt16LE(localOffset + 26);       // 本地头的 name / extra 长度可能
    const localExtraLength = buf.readUInt16LE(localOffset + 28);      // 与中央目录不同，须按本地头重算数据起点
    const start = localOffset + 30 + localNameLength + localExtraLength;   // 30 = 本地头固定部分
    const raw = buf.subarray(start, start + compressedSize);          // subarray 只是视图，不复制
    // 文件名取 basename：META-INF/jars/ 只是包内路径，落到 libs/ 时要去掉前缀。
    // stored 分支也要 Buffer.from 拷一份 —— raw 视图引用着整个 jar，不能让产物跟着它一起活着。
    wanted.add({ name: path.basename(name), data: method === 0 ? Buffer.from(raw) : zlib.inflateRawSync(raw) });
  }
  p += 46 + nameLength + extraLength + commentLength;   // 跳到下一个中央目录条目
}
// 抽不到就直接失败：静默建个空目录，编译期会报「找不到 Cardinal Components」，很难往回追。
if (wanted.size === 0) throw new Error('这个 jar 里没有 META-INF/jars 内嵌库：' + jarPath);
fs.mkdirSync(outDir, { recursive: true });   // 首次复刻时 libs/ 还不存在；recursive 让重复执行也安全
for (const entry of wanted) {
  const file = path.join(outDir, entry.name);
  fs.writeFileSync(file, entry.data);
  console.log('写出 ' + file + '（' + entry.data.length + ' 字节）');
}
