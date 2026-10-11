/**
 * 递增模组版本号 —— 每次改动都要先跑一次（规则见 README 2.4）。
 *
 *   node tools\bump-version.mjs              # 末位 +1：2.0.0 -> 2.0.1
 *   node tools\bump-version.mjs --minor      # 次位 +1、末位归零：2.0.1 -> 2.1.0
 *   node tools\bump-version.mjs --major      # 首位 +1、其余归零：2.1.0 -> 3.0.0
 *   node tools\bump-version.mjs --set 2.0.0  # 直接指定（改规则、跳版本时用）
 *
 * 版本号写在 gradle.properties 的 mod_version：它是唯一版本来源 —— jar 文件名与 fabric.mod.json 的版本
 * 都由构建期的 processResources 从它展开，所以只需要改这一处。
 *
 * 输入：gradle.properties 里的 mod_version=x.y.z。
 * 输出：就地改写 gradle.properties 的那一行（其余内容一字不动）；stdout 打印「旧 -> 新」与后续手工步骤。
 * 依赖：只用 Node 内置的 node:fs / node:url / node:path；无需构建、无需先跑测试。
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

// 相对脚本自身定位工程根：从任意工作目录调用都行（不要改成 process.cwd()，那会依赖调用者的位置）。
const file = join(dirname(fileURLToPath(import.meta.url)), '..', 'gradle.properties');
const text = readFileSync(file, 'utf8');
// 多行模式 + 整行锚定：gradle.properties 里还有别的 x.y.z（MC 版本等），只有 mod_version= 这一行算数；
// 行尾的 \s* 是用来容忍尾随空格与 CRLF 残留的。
const m = /^mod_version=(\d+)\.(\d+)\.(\d+)\s*$/m.exec(text);
if (!m) throw new Error('gradle.properties 里找不到 mod_version=x.y.z');
const major = Number(m[1]);
const minor = Number(m[2]);
const patch = Number(m[3]);
const flags = process.argv.slice(2);
let next;
if (flags[0] === '--set') {
  next = String(flags[1] ?? '');            // 显式值：不再 +1；选项缺值时给空串，让下面的格式校验去报错
} else if (flags.includes('--major')) {     // 用 includes 而不是 flags[0]：允许和别的开关并列写、顺序随意
  next = `${major + 1}.0.0`;
} else if (flags.includes('--minor')) {
  next = `${major}.${minor + 1}.0`;
} else {
  next = `${major}.${minor}.${patch + 1}`;  // 无参数 = 末位 +1，即 README 13.13 要求的「每次改动前先跑一次」
}
// 先校验再落盘：把 gradle.properties 写坏会让下一次构建直接失败。
if (!/^\d+\.\d+\.\d+$/.test(next)) throw new Error('版本格式必须是 x.y.z，收到：' + next);
// 只替换 mod_version 那一行，文件其余内容（含 LF 换行）原样保留；
// 绝不要改用 pwsh 的 Get-Content | Set-Content 改写工程文件（README 9.16）。
writeFileSync(file, text.replace(/^mod_version=.*$/m, 'mod_version=' + next), 'utf8');
console.log(`${major}.${minor}.${patch} -> ${next}`);
console.log('接下来：构建 -> 删掉 mods 里旧的 durahud-*.jar -> 复制新的 -> 在 README 14 节追加一条');
