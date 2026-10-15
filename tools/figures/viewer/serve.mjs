// The figure viewer's server: serves the page, three.js from node_modules, and
// the .glb files the tools make, and can re-run the wardrobe from the page.
// Only listens on this computer.
//   node tools/figures/viewer/serve.mjs      then open http://localhost:5178
import { createServer } from 'node:http';
import { execFile } from 'node:child_process';
import { readFile, readdir, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, extname, join, normalize, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const TOOLS = join(HERE, '..');
const PORT = Number(process.env.PORT ?? 5178);
const THREE = join(TOOLS, 'node_modules/three');

// the folders whose .glb files the page lists, by the name it shows
const SHELVES = {
  cast: join(TOOLS, 'out/cast'),
  variants: join(TOOLS, 'out/variants'),
  'stand-ins': join(TOOLS, 'out'),
  'APK assets': join(TOOLS, '../../rhc-android/app/src/gamersFemaleHomevisits/assets/figures'),
};

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.glb': 'model/gltf-binary', '.json': 'application/json', '.png': 'image/png' };

/** A file under `base`, or null if the path tries to leave it. */
function inside(base, rel) {
  const p = normalize(join(base, decodeURIComponent(rel)));
  return p === base || p.startsWith(base + sep) ? p : null;
}

async function shelves() {
  const out = [];
  for (const [name, dir] of Object.entries(SHELVES)) {
    if (!existsSync(dir)) { out.push({ name, files: [] }); continue; }
    const files = [];
    for (const f of (await readdir(dir)).filter((f) => f.endsWith('.glb')).sort()) {
      const s = await stat(join(dir, f));
      files.push({ name: f.replace(/\.glb$/, ''), url: `/files/${encodeURIComponent(name)}/${encodeURIComponent(f)}`, size: s.size, mtime: s.mtimeMs });
    }
    out.push({ name, files });
  }
  return out;
}

/** Runs a tool script with fixed arguments and returns what it printed. */
function run(script, args) {
  return new Promise((resolve) => {
    execFile(process.execPath, [join(TOOLS, script), ...args], { cwd: TOOLS, timeout: 300000 }, (err, stdout, stderr) =>
      resolve({ ok: !err, log: stdout + stderr }));
  });
}

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const send = (code, body, type = 'text/plain') => { res.writeHead(code, { 'content-type': type, 'cache-control': 'no-store' }); res.end(body); };
  try {
    if (url.pathname === '/api/list') return send(200, JSON.stringify(await shelves()), 'application/json');
    if (url.pathname === '/api/make' && req.method === 'POST') {
      const what = url.searchParams.get('what');
      const seed = String(Math.abs(parseInt(url.searchParams.get('seed') ?? '1', 10) || 1));
      const count = String(Math.min(30, Math.abs(parseInt(url.searchParams.get('count') ?? '6', 10) || 6)));
      const args = what === 'cast' ? [] : what === 'assets' ? ['--assets'] : what === 'random' ? ['--random', count, '--seed', seed] : null;
      if (!args) return send(400, 'what=cast|assets|random');
      return send(200, JSON.stringify(await run('wardrobe.mjs', args)), 'application/json');
    }
    let file = null;
    if (url.pathname === '/' || url.pathname === '/index.html') file = join(HERE, 'index.html');
    else if (url.pathname.startsWith('/three/')) file = inside(THREE, url.pathname.slice('/three/'.length));
    else if (url.pathname.startsWith('/files/')) {
      const [, , shelf, ...rest] = url.pathname.split('/');
      const dir = SHELVES[decodeURIComponent(shelf)];
      file = dir ? inside(dir, rest.join('/')) : null;
    } else file = inside(HERE, url.pathname.slice(1));
    if (!file || !existsSync(file) || (await stat(file)).isDirectory()) return send(404, 'not found');
    send(200, await readFile(file), TYPES[extname(file)] ?? 'application/octet-stream');
  } catch (e) {
    send(500, String(e?.stack ?? e));
  }
}).listen(PORT, '127.0.0.1', () => {
  console.log(`👀 Figure viewer: http://localhost:${PORT}  (Ctrl+C to stop)`);
  console.log(`   Showing ${Object.entries(SHELVES).map(([n, d]) => `${n} (${relative(join(TOOLS, '../..'), d).replaceAll('\\', '/')})`).join(', ')}`);
});
