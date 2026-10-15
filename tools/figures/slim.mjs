// Trims rigged glTF files for the AR kit (figure-core reads plain .glb only).
//   node slim.mjs list <file>...         what's in each file: meshes, skins, moves
//   node slim.mjs build <figures.json>   writes moves.glb and one .glb per figure
import { NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS } from '@gltf-transform/extensions';
import { prune, dedup, resample } from '@gltf-transform/functions';
import { readFileSync, mkdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const io = new NodeIO().registerExtensions(ALL_EXTENSIONS);
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../..');
const PACKS = join(HERE, 'packs');
// FigureRenderer.MAX_JOINTS: the shader holds this many joints per skin
const MAX_JOINTS = 80;
// what figure-core can't read
const UNSUPPORTED = ['KHR_draco_mesh_compression', 'EXT_meshopt_compression', 'KHR_texture_basisu'];

async function list(files) {
  for (const f of files) {
    const doc = await io.read(f);
    const root = doc.getRoot();
    console.log(`\n📦 ${f}`);
    for (const m of root.listMeshes()) {
      const tris = m.listPrimitives().reduce((n, p) => n + (p.getIndices()?.getCount() ?? 0) / 3, 0);
      console.log(`   mesh  ${m.getName()}: ${Math.round(tris)} triangles, materials ${m.listPrimitives().map((p) => p.getMaterial()?.getName()).join(', ')}`);
    }
    for (const s of root.listSkins()) console.log(`   skin  ${s.getName() || '(unnamed)'}: ${s.listJoints().length} joints`);
    const anims = root.listAnimations();
    if (anims.length) {
      console.log(`   ${anims.length} moves:`);
      for (const a of anims) {
        const end = Math.max(0, ...a.listSamplers().map((s) => s.getInput()?.getMax([])[0] ?? 0));
        console.log(`     ${a.getName()} (${end.toFixed(2)} s)`);
      }
    }
  }
}

function check(doc, name) {
  const used = doc.getRoot().listExtensionsUsed().map((e) => e.extensionName);
  const bad = used.filter((e) => UNSUPPORTED.includes(e));
  if (bad.length) throw new Error(`${name} uses ${bad.join(', ')}, which figure-core can't read`);
  for (const s of doc.getRoot().listSkins()) {
    if (s.listJoints().length > MAX_JOINTS) console.warn(`⚠️  ${name}: a skin has ${s.listJoints().length} joints, the renderer draws ${MAX_JOINTS}`);
  }
}

async function write(doc, out) {
  // keepLeaves: a fingertip bone has no children and no mesh, but moves still turn it
  await doc.transform(resample(), prune({ keepLeaves: true }), dedup());
  await io.write(out, doc);
  console.log(`✅ ${relative(ROOT, out).replaceAll('\\', '/')} (${(statSync(out).size / 1024).toFixed(0)} KB)`);
}

async function build(configPath) {
  const cfg = JSON.parse(readFileSync(configPath, 'utf8'));
  const out = join(ROOT, cfg.out);
  mkdirSync(out, { recursive: true });

  // The moves: only the ones asked for, renamed, without the pack's own mannequin mesh
  const moves = await io.read(join(PACKS, cfg.moves.from));
  const wanted = cfg.moves.clips;
  const found = new Set();
  for (const a of moves.getRoot().listAnimations()) {
    const name = wanted[a.getName()];
    if (name) { a.setName(name); found.add(name); } else a.dispose();
  }
  const missing = Object.values(wanted).filter((n) => !found.has(n));
  if (missing.length) throw new Error(`moves not found: ${missing.join(', ')}. Run "import.sh list" and fix figures.json`);
  for (const n of moves.getRoot().listNodes()) { n.setMesh(null); n.setSkin(null); }
  check(moves, 'moves');
  await write(moves, join(out, 'moves.glb'));

  // The figures: their meshes and rig, without moves of their own
  for (const [name, from] of Object.entries(cfg.figures)) {
    const fig = await io.read(join(PACKS, from));
    for (const a of fig.getRoot().listAnimations()) a.dispose();
    check(fig, name);
    await write(fig, join(out, `${name}.glb`));
  }
}

const [cmd, ...args] = process.argv.slice(2);
if (cmd === 'list') await list(args);
else if (cmd === 'build') await build(args[0] ?? join(HERE, 'figures.json'));
else { console.error('usage: node slim.mjs list <file>... | build [figures.json]'); process.exit(1); }
