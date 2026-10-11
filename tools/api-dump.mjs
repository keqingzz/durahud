// 从源码里抽取「公开 API 清单」，用于生成 README 第 8.2 节。
// 只列 public / protected 的字段与方法签名；顶层类声明本身不列（等价于文件名），
// 但嵌套的 public enum 会列出（例如 DurabilityEntry.Kind）。
//
// 用法：node tools/api-dump.mjs  >  README 的 8.2 节内容
//
// 输入：src/main/java 下的全部 .java 源码（只扫 main，不含 test）。
// 输出：stdout 的 Markdown 片段 —— 每个文件一个小标题（**/相对路径**），下面是 4 空格缩进的签名行，
//       原样贴进 README 8.2 节即可（README 9.12：清单由脚本生成，不要手抄）。
// 什么时候必须重跑：改过任何 public / protected 成员之后（README 9.12、8.7 第 8 条）。
// 依赖：只用 Node 内置模块；不需要编译、不需要先跑测试（纯文本扫描，不解析 Java 语法）。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 从脚本自身位置反推工程根（tools/ 的上一级）：在任意工作目录下调用结果都一样。
const PROJECT_DIR = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const SRC = path.join(PROJECT_DIR, 'src', 'main', 'java');   // 只扫主源码：测试里的 public 成员不算 API

/** 递归收集目录下所有 .java。这里不管顺序，最终顺序由下面统一的排序决定。 */
function walk(dir, out = []) {
  for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, item.name);
    if (item.isDirectory()) walk(full, out);
    else if (item.name.endsWith('.java')) out.push(full);
  }
  return out;
}

/**
 * 抽出一个文件里的公开签名。刻意做成纯文本扫描而不是解析 Java：这个脚本只在改过 API 后手动跑，
 * 面对的是本工程自己的代码风格，够用即可（README 8.4：tools 不参与构建）。
 * 返回已缩进好的行数组，调用方直接拼进 Markdown。
 */
function signatures(file) {
  const text = fs.readFileSync(file, 'utf8');
  const lines = text.split(/\r?\n/);
  const found = [];
  let topLevelSeen = false;   // 只跳过「第一个」类声明：它就是与文件同名的那个顶层类
  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();
    if (!/^(public|protected)\s/.test(trimmed)) continue;
    // 文件里第一个类声明等价于文件名，列出来只是噪音；它之后的类声明才可能是嵌套的 public enum。
    if (/^(public|protected)\s+(final\s+|abstract\s+)?(class|interface|record)\s/.test(trimmed)) {
      if (!topLevelSeen) {
        topLevelSeen = true;
        continue;
      }
    }
    // 多行签名：按括号配平把后续行并进来
    let full = trimmed;
    let depth = 0;
    // 数圆括号的净增量：签名可能跨多行，靠它判断到哪一行才算结束
    // （本工程的签名里不会出现字符串或注释中的括号，所以不用管这两种情况）。
    const count = (s) => {
      let n = 0;
      for (const ch of s) n += ch === '(' ? 1 : ch === ')' ? -1 : 0;
      return n;
    };
    depth += count(full);
    while (depth > 0 && i + 1 < lines.length) {   // 括号没配平就把下一行并进来，且不越过文件末尾
      i++;
      full += ' ' + lines[i].trim();
      depth += count(lines[i]);
    }
    // 依次：压平多行与多空格 → 去掉行尾的 {（只要签名不要方法体）→ 去掉行尾的 // 注释
    // （多行签名并进来时，可能连下一行的注释一起带进来）→ 去掉首尾空白。
    full = full.replace(/\s+/g, ' ').replace(/\s*\{\s*$/, '').replace(/\s*\/\/.*$/, '').trim();
    // 注意：这里的条件与替换互斥（不以 ; 结尾时 /;$/ 匹配不到任何东西），这一行实际是空操作。
    // 本轮只做风格整理，逻辑保持原样，不要顺手改成去掉/补上分号。
    if (!full.endsWith(';')) full = full.replace(/;$/, '');
    // 4 空格缩进即 Markdown 代码块，贴进 README 8.2 就是现成的签名块。
    found.push('    ' + full);
  }
  return found;
}

// 按「相对路径的 posix 形式」排序：Windows 的反斜杠会让顺序与别的机器不同，而 README 里的清单必须稳定。
const files = walk(SRC).sort((a, b) => {
  const ra = path.relative(SRC, a).split(path.sep).join('/');
  const rb = path.relative(SRC, b).split(path.sep).join('/');
  return ra < rb ? -1 : ra > rb ? 1 : 0;   // 显式比较，不用 localeCompare：不受本机区域设置影响
});
const blocks = [];
for (const file of files) {
  const rel = path.relative(SRC, file).split(path.sep).join('/');   // 统一成 /，与 README 里的写法一致
  const body = signatures(file);
  if (body.length === 0) continue;                            // 一个公开成员都没有的文件不占标题
  blocks.push('**/' + rel + '**\n' + body.join('\n'));   // 标题格式与 README 8.2 的现有内容对齐
}
console.log(blocks.join('\n\n'));   // 一次性打印：便于重定向进文件，也不会与其它输出交错
