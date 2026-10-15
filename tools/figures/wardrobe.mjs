// The wardrobe: dresses rigged bodies in clothes made from the body itself.
// Each garment is the part of the body's skin it covers (worked out from the
// bones: how far down the arm a sleeve goes, where the hem sits), cut cleanly
// along that line, puffed out a few millimetres and given a cloth pattern. It
// keeps the body's skin weights, so it moves with any move the body can do,
// and the skin under it is removed so elbows can't poke through.
//   node wardrobe.mjs                   the cast in wardrobe.json → out/cast/
//   node wardrobe.mjs --random 12 [--seed 7]   random people → out/variants/
//   node wardrobe.mjs --assets          also copy the cast into the Homevisits APK's assets
// Bodies come from the Quaternius pack when it's unpacked in packs/, otherwise
// from standin.mjs.
import { Logger, NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS } from '@gltf-transform/extensions';
import { dedup, prune, weld } from '@gltf-transform/functions';
import { existsSync, mkdirSync, readFileSync, copyFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readBody, sub, dot, cross, len, norm, scale, add, ARM } from './lib/rig.mjs';
import { tile, png, PERIOD } from './lib/patterns.mjs';
import { makeStandin, BUILDS } from './standin.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const PACKS = join(HERE, 'packs');
const ASSETS = join(HERE, '../../rhc-android/app/src/gamersFemaleHomevisits/assets/figures');
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS);

const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

/**
 * How far inside a garment a vertex is, in metres-ish: positive inside, zero on
 * the cut line, negative outside. Cuts are where this crosses zero, so each
 * rule is a distance to its edge. `v` is from rig.vertex(), `B` the body.
 */
function field(g, v, B) {
  const { H, land } = B;
  const T = land.neck - land.pelvis; // torso length, hips to neck
  const OUT = -0.1 * H;
  const r = v.role;
  const torso = !ARM.has(r) && !['hand', 'finger', 'foot', 'toe', 'head'].includes(r);
  // 0 behind the body, 1 in front: for necklines and openings
  const front = smooth(-0.01 * H, 0.03 * H, v.front);
  switch (g.kind) {
    case 'top': {
      if (ARM.has(r)) return ((g.sleeve ?? 0.45) - v.armT) * land.upperArm;
      if (!torso) return OUT;
      const hem = v.y - (land.pelvis + (g.hem ?? 0) * T);
      let depth = 0;
      const lat = Math.abs(v.lateral);
      if (g.neck === 'vee') depth = 0.22 * T * Math.max(0, 1 - lat / (0.05 * H)) * front;
      else if (g.neck === 'scoop') depth = 0.14 * T * Math.max(0, 1 - (lat / (0.075 * H)) ** 2) * front;
      const collar = land.neck + 0.03 * T - depth - v.y;
      let f = Math.min(hem, collar);
      if (g.open) f = Math.min(f, front > 0.5 ? lat - g.open * H : f);
      return f;
    }
    case 'bottom': {
      if (r === 'thigh' || r === 'calf' || r === 'foot' || r === 'toe') return Math.min(((g.leg ?? 1.92) - v.legT) * land.thigh, r === 'thigh' ? land.pelvis + (g.waist ?? 0.15) * T - v.y : Infinity);
      if (!torso) return OUT;
      let f = land.pelvis + (g.waist ?? 0.15) * T - v.y;
      if (g.bib) { // overalls: a bib in front and two straps over the shoulders
        const lat = Math.abs(v.lateral);
        const bib = front > 0.5 ? Math.min(0.065 * H - lat, land.pelvis + g.bib * T - v.y) : -Infinity;
        const strap = 0.011 * H - Math.abs(lat - 0.052 * H);
        f = Math.max(f, bib, strap);
      }
      return f;
    }
    case 'shoes':
      if (r === 'foot' || r === 'toe') return 0.05 * H;
      if (r === 'calf') return (v.legT - (g.top ?? 1.88)) * land.thigh;
      return OUT;
    case 'gloves':
      if (r === 'hand' || r === 'finger') return 0.05 * H;
      if (r === 'lowerarm') return (v.armT - (g.cuff ?? 1.88)) * land.upperArm;
      return OUT;
    case 'hair': {
      if (r !== 'head' && r !== 'neck') return OUT;
      // where the hairline sits at the [front, sides, back], as a fraction of the
      // way from the head joint to the crown. Sides above about 0.55 clear the ears
      // (they're cut round, not covered by a flap); a bob covers them.
      const style = { short: [0.68, 0.58, -0.1], buzz: [0.7, 0.6, 0.05], bob: [0.68, 0.1, -0.45], cap: [0.64, 0.58, 0.25] }[g.style ?? 'short'];
      if (!style) throw new Error(`hair style "${g.style}": try short, buzz, bob or cap`);
      // how far round the head: +π/2 the face, 0 an ear, −π/2 the back
      const c = sub(v.p, land.headCentre);
      const a = Math.atan2(dot(c, B.forward), Math.abs(dot(c, B.left)));
      const line = a >= 0
        ? style[1] + (style[0] - style[1]) * smooth(0.3, 1.0, a)
        : style[1] + (style[2] - style[1]) * smooth(-0.45, -1.15, a);
      return v.y - (land.head + line * (land.top - land.head));
    }
    default: throw new Error(`garment kind "${g.kind}": try top, bottom, shoes, gloves or hair`);
  }
}

/** How thick each kind is, as a share of the body's height, before layers. */
const THICK = { top: 0.004, bottom: 0.0035, shoes: 0.007, gloves: 0.003, hair: 0.007 };
const LAYER = { top: 1, bottom: 0, shoes: 0, gloves: 0, hair: 1 };

/** Puts clothes on a body document, in place. `wear` is a list of {garment, pattern, colours}. */
export function dress(doc, wear, garments) {
  const B = readBody(doc);
  const { H } = B;
  // the skin to cut clothes from: the primitive with most vertices (not the eyes or underwear)
  const src = B.prims.reduce((a, b) => (b.pos.length > a.pos.length ? b : a));
  const n = src.pos.length / 3;
  const facts = Array.from({ length: n }, (_, v) => B.vertex(src, v));

  // one normal per position, so the puffed-out cloth doesn't crack at UV seams
  const key = (v) => `${Math.round(src.pos[v * 3] * 1e4)},${Math.round(src.pos[v * 3 + 1] * 1e4)},${Math.round(src.pos[v * 3 + 2] * 1e4)}`;
  const sums = new Map();
  for (let v = 0; v < n; v++) {
    const k = key(v);
    const s = sums.get(k) ?? [0, 0, 0];
    for (let a = 0; a < 3; a++) s[a] += src.nrm[v * 3 + a];
    sums.set(k, s);
  }
  const welded = Array.from({ length: n }, (_, v) => norm(sums.get(key(v))));

  // how wide each limb is, so a pattern repeats a whole number of times around it
  const radius = {};
  {
    const acc = {};
    for (const f of facts) {
      const [a, b] = B.axes[f.axis];
      const d = norm(sub(b, a));
      const ap = sub(f.p, a);
      const rr = len(sub(ap, scale(d, dot(ap, d))));
      (acc[f.axis] ??= []).push(rr);
    }
    for (const [k, rs] of Object.entries(acc)) radius[k] = rs.reduce((x, y) => x + y, 0) / rs.length;
  }

  const scene = doc.getRoot().listScenes()[0];
  const parent = B.node.getParentNode();
  const buf = doc.getRoot().listBuffers()[0] ?? doc.createBuffer();
  const acc = (type, array) => doc.createAccessor().setType(type).setArray(array).setBuffer(buf);
  const covered = []; // per garment, per vertex: is it well inside?

  for (const item of wear) {
    const g = garments[item.garment];
    if (!g) throw new Error(`no garment "${item.garment}" in wardrobe.json`);
    const f = facts.map((v) => field(g, v, B));
    covered.push(f.map((x) => x > 0.012 * H));
    const thick = H * (THICK[g.kind] + 0.003 * (g.layer ?? LAYER[g.kind])) * (g.thick ?? 1);
    // hair thins to the skin at its edge, so the cut doesn't stand off the head like a flap
    const lift = g.kind === 'hair' ? (f) => 0.2 + 0.8 * smooth(0, 0.03 * H, f) : () => 1;
    const pattern = item.pattern ?? 'solid';
    const period = (g.period ?? PERIOD[pattern]) * (H / 1.7);

    const out = { pos: [], nrm: [], uv: [], joints: [], weights: [] };
    const corner = (v) => ({ p: facts[v].p, n: welded[v], w: weightsOf(src, v), f: f[v] });
    for (let t = 0; t < src.idx.length; t += 3) {
      const ids = [src.idx[t], src.idx[t + 1], src.idx[t + 2]];
      if (ids.every((v) => f[v] <= 0)) continue;
      const poly = clip(ids.map(corner));
      if (poly.length < 3) continue;
      // the whole triangle wraps around one axis, the one most of its corners are on
      const axes = ids.map((v) => facts[v].axis);
      const axis = axes.find((a, i) => axes.indexOf(a) !== i) ?? axes[0];
      for (let k = 1; k + 1 < poly.length; k++) {
        const tri = [poly[0], poly[k], poly[k + 1]];
        const uvs = tri.map((c) => wrap(c.p, B.axes[axis], radius[axis], period, B));
        const N = uvs[0].N;
        const us = uvs.map((u) => u.u);
        if (Math.max(...us) - Math.min(...us) > N / 2) uvs.forEach((u) => { if (u.u < 0) u.u += N; });
        tri.forEach((c, i) => {
          out.pos.push(...add(c.p, scale(c.n, thick * lift(c.f))));
          out.nrm.push(...c.n);
          out.uv.push(uvs[i].u, uvs[i].v);
          const top = [...c.w].sort((a, b) => b[1] - a[1]).slice(0, 4);
          const total = top.reduce((s, [, w]) => s + w, 0);
          for (let q = 0; q < 4; q++) { out.joints.push(top[q]?.[0] ?? 0); out.weights.push(top[q] ? top[q][1] / total : 0); }
        });
      }
    }
    if (!out.pos.length) { console.warn(`⚠️  ${item.garment} covers nothing on this body`); continue; }

    const count = out.pos.length / 3;
    const texture = doc.createTexture(`${item.garment}_${pattern}`).setImage(png(tile(pattern, item.colours ?? ['#888888']))).setMimeType('image/png');
    const material = doc.createMaterial(item.garment).setBaseColorTexture(texture).setRoughnessFactor(0.92).setMetallicFactor(0);
    const prim = doc.createPrimitive()
      .setAttribute('POSITION', acc('VEC3', new Float32Array(out.pos)))
      .setAttribute('NORMAL', acc('VEC3', new Float32Array(out.nrm)))
      .setAttribute('TEXCOORD_0', acc('VEC2', new Float32Array(out.uv)))
      .setAttribute('JOINTS_0', acc('VEC4', new Uint16Array(out.joints)))
      .setAttribute('WEIGHTS_0', acc('VEC4', new Float32Array(out.weights)))
      .setIndices(acc('SCALAR', count > 65535 ? Uint32Array.from({ length: count }, (_, i) => i) : Uint16Array.from({ length: count }, (_, i) => i)))
      .setMaterial(material);
    const node = doc.createNode(item.garment).setMesh(doc.createMesh(item.garment).addPrimitive(prim)).setSkin(B.skin);
    (parent ?? scene).addChild(node);
  }

  // take away the skin that's well inside any garment
  if (covered.length) {
    const hidden = (v) => covered.some((c) => c[v]);
    const kept = [];
    for (let t = 0; t < src.idx.length; t += 3) {
      if (!(hidden(src.idx[t]) && hidden(src.idx[t + 1]) && hidden(src.idx[t + 2]))) kept.push(src.idx[t], src.idx[t + 1], src.idx[t + 2]);
    }
    src.prim.setIndices(acc('SCALAR', n > 65535 ? Uint32Array.from(kept) : Uint16Array.from(kept)));
  }
  return B;
}

/** A vertex's joints and weights as [[joint, weight]...]. */
function weightsOf(src, v) {
  const out = [];
  for (let k = 0; k < 4; k++) if (src.weights[v * 4 + k] > 0) out.push([src.joints[v * 4 + k], src.weights[v * 4 + k]]);
  return out;
}

/** Cuts a triangle down to the part with f > 0 (0, 3 or 4 corners). Corners are {p, n, w, f}. */
function clip(tri) {
  const out = [];
  for (let i = 0; i < 3; i++) {
    const a = tri[i], b = tri[(i + 1) % 3];
    if (a.f > 0) out.push(a);
    if ((a.f > 0) !== (b.f > 0)) out.push(between(a, b, a.f / (a.f - b.f)));
  }
  return out;
}

function between(a, b, t) {
  const w = new Map();
  for (const [j, x] of a.w) w.set(j, (w.get(j) ?? 0) + x * (1 - t));
  for (const [j, x] of b.w) w.set(j, (w.get(j) ?? 0) + x * t);
  return {
    p: a.p.map((x, i) => x + (b.p[i] - x) * t),
    n: norm(a.n.map((x, i) => x + (b.n[i] - x) * t)),
    w: [...w],
    f: 0,
  };
}

/**
 * Pattern coordinates: around the axis (u, a whole number N of tiles round,
 * with the join at the back) and along it (v, in tiles).
 */
function wrap(p, [a, b], r, period, B) {
  const d = norm(sub(b, a));
  const ap = sub(p, a);
  const along = dot(ap, d);
  const radial = sub(ap, scale(d, along));
  let ref = sub(B.forward, scale(d, dot(B.forward, d)));
  if (len(ref) < 1e-3) ref = sub(B.up, scale(d, dot(B.up, d)));
  ref = norm(ref);
  const side = cross(d, ref);
  const angle = Math.atan2(dot(radial, side), dot(radial, ref));
  const N = Math.max(1, Math.round((2 * Math.PI * r) / period));
  return { u: (angle / (2 * Math.PI)) * N, v: along / period, N };
}

// ---- recipes ---------------------------------------------------------------

async function loadBody(spec, overrides = {}) {
  if (spec.pack && existsSync(join(PACKS, spec.pack))) {
    const doc = await io.read(join(PACKS, spec.pack));
    for (const a of doc.getRoot().listAnimations()) a.dispose(); // moves come from moves.glb
    return { doc, from: spec.pack };
  }
  const build = { ...BUILDS[spec.standin ?? 'female'], ...overrides };
  return { doc: makeStandin(build), from: `stand-in (${spec.standin ?? 'female'})` };
}

async function save(doc, file) {
  doc.setLogger(new Logger(Logger.Verbosity.WARN));
  await doc.transform(weld(), prune({ keepLeaves: true }), dedup());
  mkdirSync(dirname(file), { recursive: true });
  await io.write(file, doc);
}

/** A seeded random number generator (mulberry32), so `--seed` gives the same people again. */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function randomPerson(cfg, rand) {
  const pick = (xs) => xs[Math.floor(rand() * xs.length)];
  const R = cfg.random;
  const wear = [];
  const add1 = (slot) => {
    const garment = pick(R[slot]);
    const pattern = pick(R.patterns[cfg.garments[garment].kind] ?? ['solid']);
    const colours = pattern === 'solid' || pattern === 'marle' || pattern === 'knit' ? [pick(R.colours)] : [pick(R.colours), pick(R.colours), pick(R.colours)];
    if (pattern === 'denim') colours[0] = pick(R.denim);
    wear.push({ garment, pattern, colours });
  };
  add1('tops'); add1('bottoms'); add1('shoes');
  const hair = pick(R.hairColours);
  if (rand() < 0.85) wear.push({ garment: pick(R.hair), pattern: 'solid', colours: [hair] });
  const body = pick(Object.keys(cfg.bodies));
  return { body, height: undefined, skin: pick(R.skins), brows: hair, wear, heightJitter: (rand() - 0.5) * 0.12 };
}

/** Eyebrow colour: the person's own `brows`, else their hair's (not a cap's), else dark brown. */
function browsOf(person, garments) {
  if (person.brows) return person.brows;
  const hair = person.wear.find((w) => garments[w.garment]?.kind === 'hair' && garments[w.garment].style !== 'cap');
  return hair?.colours[0] ?? '#3a2a1e';
}

const args = process.argv.slice(2);
const flag = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] ?? true : undefined; };
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const cfg = JSON.parse(readFileSync(join(HERE, 'wardrobe.json'), 'utf8'));
  const random = flag('--random');
  if (random) {
    const rand = rng(Number(flag('--seed') ?? Date.now() % 100000));
    for (let i = 1; i <= Number(random); i++) {
      const p = randomPerson(cfg, rand);
      const spec = cfg.bodies[p.body];
      const base = BUILDS[spec.standin ?? 'female'];
      const { doc, from } = await loadBody(spec, { skin: p.skin, brows: browsOf(p, cfg.garments), height: base.height + p.heightJitter });
      dress(doc, p.wear, cfg.garments);
      const file = join(HERE, 'out/variants', `v${String(i).padStart(2, '0')}.glb`);
      await save(doc, file);
      console.log(`🎲 v${String(i).padStart(2, '0')}: ${from}, ${p.wear.map((w) => `${w.garment} (${w.pattern})`).join(', ')}`);
    }
  } else {
    for (const [name, person] of Object.entries(cfg.cast)) {
      const spec = cfg.bodies[person.body];
      if (!spec) throw new Error(`${name}: no body "${person.body}" in wardrobe.json`);
      const { doc, from } = await loadBody(spec, { ...(person.height && { height: person.height }), ...(person.skin && { skin: person.skin }), brows: browsOf(person, cfg.garments) });
      dress(doc, person.wear, cfg.garments);
      const file = join(HERE, 'out/cast', `${name}.glb`);
      await save(doc, file);
      console.log(`👕 ${name}: ${from}, ${person.wear.map((w) => w.garment).join(', ')}`);
      if (flag('--assets')) {
        mkdirSync(ASSETS, { recursive: true });
        copyFileSync(file, join(ASSETS, `${name}.glb`));
        if (name === cfg.test) copyFileSync(file, join(ASSETS, 'test.glb'));
      }
    }
    if (flag('--assets')) console.log(`📦 Copied into ${ASSETS} (${cfg.test}.glb is also test.glb, the AR test screen's figure)`);
  }
  console.log('👀 Look at them: node viewer/serve.mjs');
}
