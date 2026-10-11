// 极简 PNG 编解码（只支持 8 位、非隔行；解码 0/2/3/4/6 全部颜色类型）。
// 用途：离线预览工具要把原版 widgets.png / bars.png 取出来合成，再写出 PNG。
//
// 输入 / 输出：
//   decodePng(buffer)             -> { width, height, data }，data 已展开成 RGBA（非预乘）。
//   encodePng(width, height, rgba) -> Buffer，固定写 8 位、颜色类型 6（RGBA）、非隔行。
// 怎么跑：不单独跑，被 assets.mjs 与 preview.mjs import（入口是 node tools/preview.mjs）。
// 依赖：只用 Node 内置的 node:zlib；无第三方依赖、无 npm install、无需先构建或先跑测试。
//       Node 14+ 即可（本机实测 v22.19.0）。刻意不用 sharp / pngjs：tools 要「拷贝工程即可运行」。
import zlib from 'node:zlib';

// PNG 的 8 字节固定文件头（89 50 4E 47 0D 0A 1A 0A）：解析前先校验，免得把别的文件当 PNG 硬解。
const SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

// CRC32 查表。PNG 每个 chunk 末尾都要带自己的 CRC，校验不过时解码器会拒绝整个文件，
// 所以这里必须与 PNG 规范一致，不能图省事省掉。表在模块加载时建一次，逐字节的循环留给 crc32()。
const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;   // 0xedb88320 = 反射式 CRC-32 多项式
    table[n] = c;
  }
  return table;
})();

// 标准 CRC-32：初值与末尾都按位取反（PNG 规范要求），结果转成无符号 32 位。
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

// PNG 行过滤器 4（Paeth）：用左 / 上 / 左上三个已还原像素预测当前像素，取与 p 最接近的那个。
function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

/**
 * 解码 PNG 成 RGBA。只支持 8 位、非隔行：原版图集就是这个格式，
 * 支持隔行要另写一套 Adam7 行重排，离线预览工具不值得为此变复杂。
 */
export function decodePng(buffer) {
  if (!buffer.subarray(0, 8).equals(SIGNATURE)) throw new Error('不是 PNG 文件');   // 快速失败，错误信息要能一眼看出喂错了文件
  let offset = 8;                                    // 跳过 8 字节文件头，指向第一个 chunk
  let width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;   // 全部来自 IHDR，读完才知道怎么解
  let palette = null, transparency = null;           // 只有颜色类型 3（调色板）用得到
  const idat = [];                                   // IDAT 可能被切成多个 chunk，按出现顺序收集后再拼
  while (offset < buffer.length) {
    const length = buffer.readUInt32BE(offset);                       // chunk 数据段字节数（长度字段本身不算）
    const type = buffer.toString('ascii', offset + 4, offset + 8);    // 4 字节 ASCII 类型码
    const data = buffer.subarray(offset + 8, offset + 8 + length);    // 数据段，不含长度 / 类型 / CRC
    offset += 12 + length;                                           // 4 长度 + 4 类型 + 数据 + 4 CRC
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);      // IHDR 必须是第一个 chunk：宽高与格式都在这里
      height = data.readUInt32BE(4);
      bitDepth = data[8];                // 位深
      colorType = data[9];               // 颜色类型
      interlace = data[12];              // 0 = 非隔行
    } else if (type === 'PLTE') {
      palette = Buffer.from(data);       // 调色板：每项 3 字节 RGB
    } else if (type === 'tRNS') {
      transparency = Buffer.from(data);  // 调色板的逐项 alpha（可选块）
    } else if (type === 'IDAT') {
      idat.push(Buffer.from(data));      // 复制一份：data 是原 buffer 的视图，后面要 concat，别留引用
    } else if (type === 'IEND') {
      break;                             // 结束块之后没有要读的了
    }
  }
  if (bitDepth !== 8) throw new Error('只支持 8 位深度，实际 ' + bitDepth);
  if (interlace !== 0) throw new Error('不支持隔行 PNG');
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];   // 灰度 / 真彩 / 调色板 / 灰度+A / RGBA
  if (!channels) throw new Error('不支持的颜色类型 ' + colorType);
  const raw = zlib.inflateSync(Buffer.concat(idat));   // IDAT 是 zlib 流（不是裸 deflate），这里一次解出全部扫描线
  const stride = width * channels;                     // 一行像素的字节数，不含行首那个过滤器字节
  const pixels = Buffer.alloc(height * stride);
  let pos = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[pos++];                         // 每行开头 1 字节：本行用哪种过滤器
    const line = raw.subarray(pos, pos + stride);
    pos += stride;
    const out = pixels.subarray(y * stride, (y + 1) * stride);
    const prior = y > 0 ? pixels.subarray((y - 1) * stride, y * stride) : null;   // 上一行已还原的像素；第 0 行没有
    for (let x = 0; x < stride; x++) {
      const a = x >= channels ? out[x - channels] : 0;                       // 左邻（本行已还原）
      const b = prior ? prior[x] : 0;                                        // 上邻
      const c = prior && x >= channels ? prior[x - channels] : 0;            // 左上邻
      let value = line[x];
      // 按过滤器把预测值加回去；越出边界的一侧按 0 处理（PNG 规范）
      if (filter === 1) value += a;
      else if (filter === 2) value += b;
      else if (filter === 3) value += (a + b) >> 1;
      else if (filter === 4) value += paeth(a, b, c);
      out[x] = value & 0xff;
    }
  }
  // 统一展开成 RGBA：后面的合成代码只处理一种像素格式，颜色类型的分支全部收在这里。
  const rgba = Buffer.alloc(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    let r, g, b, alpha = 255;
    if (colorType === 0) {
      r = g = b = pixels[i];
    } else if (colorType === 2) {
      r = pixels[i * 3]; g = pixels[i * 3 + 1]; b = pixels[i * 3 + 2];
    } else if (colorType === 3) {
      const index = pixels[i];
      r = palette[index * 3]; g = palette[index * 3 + 1]; b = palette[index * 3 + 2];
      if (transparency && index < transparency.length) alpha = transparency[index];
    } else if (colorType === 4) {
      r = g = b = pixels[i * 2]; alpha = pixels[i * 2 + 1];
    } else {
      r = pixels[i * 4]; g = pixels[i * 4 + 1]; b = pixels[i * 4 + 2]; alpha = pixels[i * 4 + 3];
    }
    // PNG 是非预乘的，这里直接展开成 RGBA 供合成使用
    rgba[i * 4] = r; rgba[i * 4 + 1] = g; rgba[i * 4 + 2] = b; rgba[i * 4 + 3] = alpha;
  }
  return { width, height, data: rgba };
}

// 组装一个 chunk：长度(BE) + 类型 + 数据 + CRC。CRC 只覆盖「类型 + 数据」，长度字段不算。
function chunk(type, data) {
  const head = Buffer.alloc(8);
  head.writeUInt32BE(data.length, 0);
  head.write(type, 4, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4, 8), data])), 0);
  return Buffer.concat([head, data, crc]);
}

/**
 * 编码成 PNG。固定写颜色类型 6（RGBA）与 8 位：预览合成结果本来就是不透明的，
 * 不做调色板量化 —— 免去一套量化器，也不会引入肉眼可见的色阶。
 */
export function encodePng(width, height, rgba) {
  const ihdr = Buffer.alloc(13);   // IHDR 固定 13 字节
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;                     // 位深
  ihdr[9] = 6;                     // 颜色类型 6 = 真彩 + alpha
  // 其余字段留 0：压缩方法 deflate、过滤方法默认、非隔行
  const stride = width * 4;
  const raw = Buffer.alloc(height * (stride + 1));   // 每行前面多一个过滤器字节
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0;                       // 过滤器 0 = None：像素已是 RGBA，压缩交给 deflate
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  return Buffer.concat([
    SIGNATURE,
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),   // level 9：离线工具只写几张图，压缩率优先
    chunk('IEND', Buffer.alloc(0)),
  ]);
}
