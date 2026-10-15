// Checks .glb files before they go in the APK: the Khronos glTF validator, then
// what figure-core and the AR kit's renderer can handle.
//   node check.mjs [file.glb | dir]...   (default: out/ and the Homevisits assets)
import { NodeIO } from '@gltf-transform/core';
import validator from 'gltf-validator';
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '../..');
const ASSETS = join(ROOT, 'rhc-android/app/src/gamersFemaleHomevisits/assets/figures');
const MAX_JOINTS = 80; // FigureRenderer.MAX_JOINTS
const UNSUPPORTED = ['KHR_draco_mesh_compression', 'EXT_meshopt_compression', 'KHR_texture_basisu', 'KHR_mesh_quantization'];

const glbs = (p) => (statSync(p).isDirectory()
  ? readdirSync(p, { recursive: true }).filter((f) => f.endsWith('.glb')).map((f) => join(p, f))
  : [p]);

const args = process.argv.slice(2);
const files = (args.length ? args : [join(HERE, 'out'), ASSETS].filter(existsSync)).flatMap(glbs);
if (!files.length) { console.log('❌ No .glb files to check. Make some: node wardrobe.mjs'); process.exit(1); }

const io = new NodeIO();
let bad = 0;
for (const file of files) {
  const bytes = new Uint8Array(readFileSync(file));
  const report = await validator.validateBytes(bytes, { maxIssues: 20 });
  const problems = [];
  for (const m of report.issues.messages.filter((m) => m.severity === 0)) problems.push(`${m.code}: ${m.message} (${m.pointer ?? ''})`);
  const doc = await io.readBinary(bytes);
  const root = doc.getRoot();
  const used = root.listExtensionsUsed().map((e) => e.extensionName).filter((e) => UNSUPPORTED.includes(e));
  if (used.length) problems.push(`figure-core can't read ${used.join(', ')}`);
  for (const s of root.listSkins()) if (s.listJoints().length > MAX_JOINTS) problems.push(`skin ${s.getName()} has ${s.listJoints().length} joints; the renderer draws ${MAX_JOINTS}`);
  for (const m of root.listMeshes()) for (const p of m.listPrimitives()) {
    if (p.getMode() !== 4) problems.push(`${m.getName()}: not triangles, so it won't be drawn`);
    const w = p.getAttribute('WEIGHTS_0');
    if (w) for (let i = 0, el = []; i < w.getCount(); i++) {
      w.getElement(i, el);
      const sum = el[0] + el[1] + el[2] + el[3];
      if (Math.abs(sum - 1) > 0.01) { problems.push(`${m.getName()}: vertex ${i}'s weights add to ${sum.toFixed(3)}`); break; }
    }
  }
  const tris = root.listMeshes().reduce((n, m) => n + m.listPrimitives().reduce((k, p) => k + (p.getIndices()?.getCount() ?? p.getAttribute('POSITION').getCount()) / 3, 0), 0);
  const name = relative(ROOT, file).replaceAll('\\', '/');
  const info = `${Math.round(tris)} tris, ${root.listMaterials().length} materials, ${root.listAnimations().length} moves, ${(bytes.length / 1024).toFixed(0)} KB`;
  if (problems.length) { bad++; console.log(`❌ ${name} (${info})`); problems.forEach((p) => console.log(`     ${p}`)); }
  else console.log(`✅ ${name} (${info}${report.issues.numWarnings ? `, ${report.issues.numWarnings} validator warnings` : ''})`);
}
process.exit(bad ? 1 : 0);
