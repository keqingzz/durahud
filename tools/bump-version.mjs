/**
 * 递增模组版本号 —— 每次改动都要先跑一次（规则见 README 2.4）。
 *
 *   node tools\bump-version.mjs              # 末位 +1：2.0.0 -> 2.0.1
 *   node tools\bump-version.mjs --minor      # 次位 +1、末位归零：2.0.1 -> 2.1.0
 *   node tools\bump-version.mjs --major      # 首位 +1、其余归零：2.1.0 -> 3.0.0
 *   node tools\bump-version.mjs --set 2.0.0  # 直接指定（改规则、跳版本时用）
 *
 * 版本号写在 gradle.properties 的 mod_version，同时决定 jar 文件名与 fabric.mod.json 的版本。
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const file = join(dirname(fileURLToPath(import.meta.url)), '..', 'gradle.properties');
const text = readFileSync(file, 'utf8');
const m = /^mod_version=(\d+)\.(\d+)\.(\d+)\s*$/m.exec(text);
if (!m) throw new Error('gradle.properties 里找不到 mod_version=x.y.z');
const major = Number(m[1]);
const minor = Number(m[2]);
const patch = Number(m[3]);
const flags = process.argv.slice(2);
let next;
if (flags[0] === '--set') {
    next = String(flags[1] ?? '');
} else if (flags.includes('--major')) {
    next = `${major + 1}.0.0`;
} else if (flags.includes('--minor')) {
    next = `${major}.${minor + 1}.0`;
} else {
    next = `${major}.${minor}.${patch + 1}`;
}
if (!/^\d+\.\d+\.\d+$/.test(next)) throw new Error('版本格式必须是 x.y.z，收到：' + next);
writeFileSync(file, text.replace(/^mod_version=.*$/m, 'mod_version=' + next), 'utf8');
console.log(`${major}.${minor}.${patch} -> ${next}`);
console.log('接下来：构建 -> 删掉 mods 里旧的 durahud-*.jar -> 复制新的 -> 在 README 14 节追加一条');
