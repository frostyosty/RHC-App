// Cloth patterns as small tiling PNGs, made in code so every colour is a recipe
// rather than a file. A tile repeats every `period` metres on the garment (see
// wardrobe.mjs), so a check reads the same size on a sleeve as on a back.
import { deflateSync } from 'node:zlib';

export const TILE = 64;

/** How many metres one tile covers, by pattern, unless a garment says otherwise. */
export const PERIOD = { solid: 0.1, marle: 0.08, stripes: 0.07, check: 0.16, tartan: 0.2, denim: 0.06, knit: 0.05, hivis: 0.4, flecks: 0.25, tweed: 0.06 };

/** '#rrggbb' → [r, g, b] 0..255 */
export function rgb(hex) {
  const n = parseInt(hex.replace('#', ''), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const shade = (c, k) => c.map((v) => v * k);

/** A small fixed hash so the noise in a tile is the same every run. */
function noise(x, y, salt = 0) {
  let h = (x * 374761393 + y * 668265263 + salt * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}

/**
 * Pixels of one tile, RGBA. `colours` are hex strings; what each pattern does
 * with them is in the switch. Rows run along the garment's length (v), columns
 * around it (u).
 */
export function tile(pattern, colours) {
  const c = colours.map(rgb);
  const [a, b = shade(c[0], 0.6), d = mix(a, [255, 255, 255], 0.5)] = c;
  const px = new Uint8Array(TILE * TILE * 4);
  const N = TILE;
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const n = noise(x, y) - 0.5;
    let col;
    switch (pattern) {
      case 'solid': col = shade(a, 1 + n * 0.06); break;
      case 'marle': col = mix(a, b, noise(x, y, 1) < 0.3 ? 0.6 : 0); break;
      case 'stripes': col = y < N / 3 ? b : a; break;
      case 'check': { // buffalo check: two crossing bands, darkest where they cross
        const k = (x < N / 2) + (y < N / 2);
        col = shade(k === 2 ? b : k === 1 ? mix(a, b, 0.5) : a, 1 + n * 0.08);
        break;
      }
      case 'tartan': { // a ground, wide crossing bands, and a thin line of the third colour
        const band = (v) => (v >= 8 && v < 24 ? 1 : 0);
        const line = (v) => v === 44 || v === 45;
        const k = band(x) + band(y);
        col = line(x) || line(y) ? d : k === 2 ? b : k === 1 ? mix(a, b, 0.5) : a;
        col = shade(col, 1 + n * 0.06);
        break;
      }
      case 'denim': { // twill: diagonal light threads over the blue
        const twill = (x + y * 2) % 6 < 2;
        col = shade(mix(a, [235, 240, 245], twill ? 0.18 : 0), 1 + n * 0.12);
        break;
      }
      case 'knit': { // rows of little vees
        const vx = x % 8, vy = y % 6;
        const ridge = Math.abs(vx - 3.5) * 0.8 > vy - 1 && Math.abs(vx - 3.5) * 0.8 < vy + 1;
        col = shade(a, (ridge ? 1.08 : 0.85) + n * 0.05);
        break;
      }
      case 'hivis': // a fluoro ground with two silver reflective bands
        col = (y >= 10 && y < 16) || (y >= 22 && y < 28) ? [205, 210, 215] : shade(a, 1 + n * 0.05);
        break;
      case 'flecks': // overalls with paint on them: the ground plus spots of the other colours
        col = shade(a, 1 + n * 0.08);
        if (noise(x >> 1, y >> 1, 7) > 0.985) col = c[1 + Math.floor(noise(x >> 1, y >> 1, 9) * (c.length - 1))] ?? b;
        break;
      case 'tweed': col = mix(a, b, noise(x, y >> 1, 3) < 0.35 ? 0.7 : noise(x, y, 4) < 0.1 ? -0.3 : 0); break;
      default: throw new Error(`unknown pattern "${pattern}" (try ${Object.keys(PERIOD).join(', ')})`);
    }
    const o = (y * N + x) * 4;
    px[o] = clamp(col[0]); px[o + 1] = clamp(col[1]); px[o + 2] = clamp(col[2]); px[o + 3] = 255;
  }
  return px;
}

const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));

const CRC = new Int32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c;
});
function crc32(buf) {
  let c = -1;
  for (const byte of buf) c = CRC[(c ^ byte) & 255] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

/** RGBA pixels → PNG bytes. */
export function png(px, w = TILE, h = TILE) {
  const raw = Buffer.alloc(h * (w * 4 + 1));
  for (let y = 0; y < h; y++) Buffer.from(px.buffer, px.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; // 8-bit RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]);
}
