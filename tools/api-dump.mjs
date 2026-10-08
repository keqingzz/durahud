// 从源码里抽取「公开 API 清单」，用于生成 README 第 8.2 节。
// 只列 public / protected 的字段与方法签名；顶层类声明本身不列（等价于文件名），
// 但嵌套的 public enum 会列出（例如 DurabilityEntry.Kind）。
//
// 用法：node tools/api-dump.mjs  >  README 的 8.2 节内容
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PROJECT_DIR = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const SRC = path.join(PROJECT_DIR, 'src', 'main', 'java');

function walk(dir, out = []) {
  for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, item.name);
    if (item.isDirectory()) walk(full, out);
    else if (item.name.endsWith('.java')) out.push(full);
  }
  return out;
}

function signatures(file) {
  const text = fs.readFileSync(file, 'utf8');
  const lines = text.split(/\r?\n/);
  const found = [];
  let topLevelSeen = false;
  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();
    if (!/^(public|protected)\s/.test(trimmed)) continue;
    if (/^(public|protected)\s+(final\s+|abstract\s+)?(class|interface|record)\s/.test(trimmed)) {
      if (!topLevelSeen) {
        topLevelSeen = true;
        continue;
      }
    }
    // 多行签名：按括号配平把后续行并进来
    let full = trimmed;
    let depth = 0;
    const count = (s) => {
      let n = 0;
      for (const ch of s) n += ch === '(' ? 1 : ch === ')' ? -1 : 0;
      return n;
    };
    depth += count(full);
    while (depth > 0 && i + 1 < lines.length) {
      i++;
      full += ' ' + lines[i].trim();
      depth += count(lines[i]);
    }
    full = full.replace(/\s+/g, ' ').replace(/\s*\{\s*$/, '').replace(/\s*\/\/.*$/, '').trim();
    if (!full.endsWith(';')) full = full.replace(/;$/, '');
    found.push('    ' + full);
  }
  return found;
}

const files = walk(SRC).sort((a, b) => {
  const ra = path.relative(SRC, a).split(path.sep).join('/');
  const rb = path.relative(SRC, b).split(path.sep).join('/');
  return ra < rb ? -1 : ra > rb ? 1 : 0;
});
const blocks = [];
for (const file of files) {
  const rel = path.relative(SRC, file).split(path.sep).join('/');
  const body = signatures(file);
  if (body.length === 0) continue;
  blocks.push('**/' + rel + '**\n' + body.join('\n'));
}
console.log(blocks.join('\n\n'));
