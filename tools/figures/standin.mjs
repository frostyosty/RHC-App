// A stand-in person, made in code: a smooth mannequin body (soft-joined shapes,
// meshed with surface nets), rigged with UE5-style bone names like Quaternius'
// packs, with its own Idle, Walk, Wave, Talk and Sit. It lets the wardrobe and
// the viewer work before any pack is imported, and gives the cast different
// builds. Facing +Z, Y up, metres, standing on y = 0.
//   node standin.mjs [build...]     writes out/standin_<build>.glb (default: every build)
import { Document, NodeIO } from '@gltf-transform/core';
import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));

/** Body builds, in metres for a 1.75 m person; everything is scaled to `height`. */
export const BUILDS = {
  female: { height: 1.66, shoulders: 0.172, chest: [0.15, 0.14, 0.1], waist: 0.122, hips: 0.178, bust: 0.056, belly: 0, skin: '#e8b996' },
  male: { height: 1.78, shoulders: 0.2, chest: [0.18, 0.15, 0.112], waist: 0.148, hips: 0.162, bust: 0, belly: 0, skin: '#d9a07a' },
  older: { height: 1.72, shoulders: 0.188, chest: [0.17, 0.145, 0.11], waist: 0.158, hips: 0.165, bust: 0, belly: 0.035, skin: '#efc2a0' },
  solid: { height: 1.85, shoulders: 0.215, chest: [0.2, 0.16, 0.125], waist: 0.172, hips: 0.178, bust: 0, belly: 0.03, skin: '#8d5a3b' },
};

/** A lip colour for a skin: the skin, darker and rosier. */
export function lipsFor(skin) {
  const [r, g, b] = skin.match(/\w\w/g).map((h) => parseInt(h, 16));
  return '#' + [r * 0.74, g * 0.5, b * 0.52].map((x) => Math.round(x).toString(16).padStart(2, '0')).join('');
}

// ---- the rig ---------------------------------------------------------------

const BONES = [ // name, parent
  ['root', null], ['pelvis', 'root'], ['spine_01', 'pelvis'], ['spine_02', 'spine_01'], ['spine_03', 'spine_02'],
  ['neck_01', 'spine_03'], ['head', 'neck_01'],
  ...['l', 'r'].flatMap((s) => [
    [`clavicle_${s}`, 'spine_03'], [`upperarm_${s}`, `clavicle_${s}`], [`lowerarm_${s}`, `upperarm_${s}`], [`hand_${s}`, `lowerarm_${s}`],
    [`thigh_${s}`, 'pelvis'], [`calf_${s}`, `thigh_${s}`], [`foot_${s}`, `calf_${s}`], [`ball_${s}`, `foot_${s}`],
  ]),
];
const ARM_DOWN = (32 * Math.PI) / 180; // A-pose: arms this far out from hanging straight down

/** World positions of the joints at rest, for a 1.75 m reference body (scaled later). */
function jointsAt(b) {
  const J = {
    root: [0, 0, 0], pelvis: [0, 0.95, 0], spine_01: [0, 1.04, 0], spine_02: [0, 1.17, 0], spine_03: [0, 1.3, 0],
    neck_01: [0, 1.46, 0], head: [0, 1.56, 0.01],
  };
  for (const [s, x] of [['l', 1], ['r', -1]]) {
    const dir = [x * Math.sin(ARM_DOWN), -Math.cos(ARM_DOWN), 0];
    const ua = [x * b.shoulders, 1.4, 0];
    const la = ua.map((v, i) => v + dir[i] * 0.28);
    J[`clavicle_${s}`] = [x * 0.02, 1.42, 0.02];
    J[`upperarm_${s}`] = ua;
    J[`lowerarm_${s}`] = la;
    J[`hand_${s}`] = la.map((v, i) => v + dir[i] * 0.25);
    J[`thigh_${s}`] = [x * 0.09, 0.92, 0];
    J[`calf_${s}`] = [x * 0.095, 0.5, 0.01];
    J[`foot_${s}`] = [x * 0.1, 0.085, -0.015];
    J[`ball_${s}`] = [x * 0.1, 0.02, 0.12];
  }
  return J;
}

// ---- the body's shape ------------------------------------------------------

// Signed distances (negative inside). An ellipsoid (Inigo Quilez's bound) and a
// capsule whose radius goes from r1 to r2 along it.
function ellipsoid(p, c, r) {
  const q = [(p[0] - c[0]) / r[0], (p[1] - c[1]) / r[1], (p[2] - c[2]) / r[2]];
  const q2 = [q[0] / r[0], q[1] / r[1], q[2] / r[2]];
  const k0 = Math.hypot(...q), k1 = Math.hypot(...q2);
  return k1 === 0 ? -Math.min(...r) : (k0 * (k0 - 1)) / k1;
}
function cone(p, a, b, r1, r2) {
  const ab = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
  const ap = [p[0] - a[0], p[1] - a[1], p[2] - a[2]];
  const t = Math.max(0, Math.min(1, (ap[0] * ab[0] + ap[1] * ab[1] + ap[2] * ab[2]) / (ab[0] ** 2 + ab[1] ** 2 + ab[2] ** 2)));
  return Math.hypot(ap[0] - ab[0] * t, ap[1] - ab[1] * t, ap[2] - ab[2] * t) - (r1 + (r2 - r1) * t);
}
/** Smooth minimum: joins two shapes with a fillet about k wide. */
function smin(a, b, k) {
  const h = Math.max(k - Math.abs(a - b), 0) / k;
  return Math.min(a, b) - (h * h * k) / 4;
}

/** The shapes the body is made of, each moved by one bone, at the 1.75 m reference size. */
function shapesOf(b, J) {
  const S = [];
  const ell = (bone, c, r, k) => S.push({ bone, k, d: (p) => ellipsoid(p, c, r) });
  const cap = (bone, a, c, r1, r2, k) => S.push({ bone, k, d: (p) => cone(p, a, c, r1, r2) });
  ell('pelvis', [0, 0.92, -0.005], [b.hips, 0.11, 0.105], 0.05);
  ell('spine_01', [0, 1.06, 0.005 + b.belly * 0.6], [b.waist, 0.12, 0.095 + b.belly], 0.06);
  ell('spine_02', [0, 1.19, 0], [(b.waist + b.chest[0]) / 2, 0.1, 0.1 + b.belly * 0.5], 0.06);
  ell('spine_03', [0, 1.31, 0], b.chest, 0.06);
  if (b.bust > 0) for (const x of [1, -1]) ell('spine_03', [x * 0.062, 1.265, 0.072], [b.bust, b.bust * 0.88, b.bust * 0.95], 0.04);
  cap('neck_01', [0, 1.38, -0.005], [0, 1.57, 0], 0.052, 0.047, 0.04);
  ell('head', [0, 1.645, 0.012], [0.078, 0.108, 0.095], 0.03);
  ell('head', [0, 1.592, 0.035], [0.06, 0.05, 0.06], 0.05);
  cap('head', [0, 1.64, 0.098], [0, 1.615, 0.107], 0.01, 0.0135, 0.016);
  for (const [s, x] of [['l', 1], ['r', -1]]) {
    ell('head', [x * 0.079, 1.636, 0], [0.012, 0.026, 0.018], 0.012);
    cap(`clavicle_${s}`, [0, 1.415, -0.005], [x * (b.shoulders - 0.01), 1.4, 0], 0.066, 0.056, 0.05);
    const hand = J[`hand_${s}`];
    const dir = [x * Math.sin(ARM_DOWN), -Math.cos(ARM_DOWN), 0];
    cap(`upperarm_${s}`, J[`upperarm_${s}`], J[`lowerarm_${s}`], 0.048, 0.037, 0.03);
    cap(`lowerarm_${s}`, J[`lowerarm_${s}`], hand, 0.036, 0.026, 0.015);
    cap(`hand_${s}`, hand, hand.map((v, i) => v + dir[i] * 0.15), 0.029, 0.024, 0.012);
    cap(`thigh_${s}`, J[`thigh_${s}`], J[`calf_${s}`], 0.083, 0.052, 0.04);
    cap(`calf_${s}`, J[`calf_${s}`], J[`foot_${s}`], 0.052, 0.034, 0.03);
    cap(`foot_${s}`, [x * 0.1, 0.042, -0.045], [x * 0.1, 0.038, 0.1], 0.042, 0.037, 0.03);
    cap(`ball_${s}`, [x * 0.1, 0.037, 0.1], [x * 0.1, 0.031, 0.165], 0.037, 0.031, 0.02);
  }
  return S;
}

// ---- meshing ---------------------------------------------------------------

/** Surface nets: one vertex per grid cell the surface crosses, one quad per crossed grid edge. */
function surfaceNets(sdf, lo, hi, h) {
  const n = [0, 1, 2].map((a) => Math.ceil((hi[a] - lo[a]) / h) + 1);
  const at = (i, j, k) => i + n[0] * (j + n[1] * k);
  const field = new Float32Array(n[0] * n[1] * n[2]);
  for (let k = 0; k < n[2]; k++) for (let j = 0; j < n[1]; j++) for (let i = 0; i < n[0]; i++) {
    field[at(i, j, k)] = sdf([lo[0] + i * h, lo[1] + j * h, lo[2] + k * h]);
  }
  const cell = new Int32Array(n[0] * n[1] * n[2]).fill(-1);
  const pos = [];
  const corners = [];
  for (let c = 0; c < 8; c++) corners.push([c & 1, (c >> 1) & 1, (c >> 2) & 1]);
  const edges = [];
  for (let a = 0; a < 8; a++) for (let b = a + 1; b < 8; b++) {
    const d = corners[a].reduce((s, v, i) => s + Math.abs(v - corners[b][i]), 0);
    if (d === 1) edges.push([a, b]);
  }
  for (let k = 0; k < n[2] - 1; k++) for (let j = 0; j < n[1] - 1; j++) for (let i = 0; i < n[0] - 1; i++) {
    const v = corners.map(([x, y, z]) => field[at(i + x, j + y, k + z)]);
    let inside = 0;
    for (const x of v) if (x < 0) inside++;
    if (inside === 0 || inside === 8) continue;
    const sum = [0, 0, 0];
    let m = 0;
    for (const [a, b] of edges) {
      if ((v[a] < 0) === (v[b] < 0)) continue;
      const t = v[a] / (v[a] - v[b]);
      for (let q = 0; q < 3; q++) sum[q] += corners[a][q] + (corners[b][q] - corners[a][q]) * t;
      m++;
    }
    cell[at(i, j, k)] = pos.length / 3;
    pos.push(lo[0] + (i + sum[0] / m) * h, lo[1] + (j + sum[1] / m) * h, lo[2] + (k + sum[2] / m) * h);
  }
  const tris = [];
  for (let k = 1; k < n[2] - 1; k++) for (let j = 1; j < n[1] - 1; j++) for (let i = 1; i < n[0] - 1; i++) {
    const inside = field[at(i, j, k)] < 0;
    for (let a = 0; a < 3; a++) {
      const e = [0, 0, 0]; e[a] = 1;
      if (inside === field[at(i + e[0], j + e[1], k + e[2])] < 0) continue;
      // the four cells around this edge
      const u = [0, 0, 0], w = [0, 0, 0];
      u[(a + 1) % 3] = 1; w[(a + 2) % 3] = 1;
      const quad = [[0, 0], [1, 0], [1, 1], [0, 1]].map(([x, y]) =>
        cell[at(i - u[0] * (1 - x) - w[0] * (1 - y), j - u[1] * (1 - x) - w[1] * (1 - y), k - u[2] * (1 - x) - w[2] * (1 - y))]);
      if (quad.some((q) => q < 0)) continue;
      // going round the cells in this order faces +axis; face out, towards the outside node
      if (!inside) quad.reverse();
      tris.push(quad[0], quad[1], quad[2], quad[0], quad[2], quad[3]);
    }
  }
  return { pos: new Float32Array(pos), tris: Uint32Array.from(tris) };
}

/** A small UV sphere, for the eyes. */
function sphere(c, r, rings = 6, segs = 10) {
  const pos = [], nrm = [], tris = [];
  for (let i = 0; i <= rings; i++) for (let j = 0; j <= segs; j++) {
    const th = (i / rings) * Math.PI, ph = (j / segs) * 2 * Math.PI;
    const n = [Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph)];
    nrm.push(...n);
    pos.push(c[0] + n[0] * r, c[1] + n[1] * r, c[2] + n[2] * r);
  }
  for (let i = 0; i < rings; i++) for (let j = 0; j < segs; j++) {
    const a = i * (segs + 1) + j, b = a + segs + 1;
    tris.push(a, a + 1, b, b, a + 1, b + 1);
  }
  return { pos, nrm, tris };
}

/**
 * A tube along a curve, flat against the skin: for eyebrows and the mouth.
 * `pts` are [x, y, z] on the face, `nrmAt(p)` the skin's outward normal there,
 * `w(t)` the half-width across the face and `d` the half-depth out of it.
 */
function strip(pts, nrmAt, w, d, segs = 6) {
  const pos = [], nrm = [], tris = [];
  const n = pts.length;
  for (let i = 0; i < n; i++) {
    const p = pts[i];
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(n - 1, i + 1)];
    let t = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
    const tl = Math.hypot(...t) || 1; t = t.map((x) => x / tl);
    const out = nrmAt(p);
    // across the face: perpendicular to both the curve and the normal
    const ac = [t[1] * out[2] - t[2] * out[1], t[2] * out[0] - t[0] * out[2], t[0] * out[1] - t[1] * out[0]];
    const width = w(i / (n - 1));
    for (let j = 0; j < segs; j++) {
      const th = (j / segs) * 2 * Math.PI;
      const c = Math.cos(th), s = Math.sin(th);
      pos.push(...[0, 1, 2].map((k) => p[k] + ac[k] * c * width + out[k] * s * d));
      const nn = [0, 1, 2].map((k) => ac[k] * c * d + out[k] * s * width);
      const l = Math.hypot(...nn) || 1;
      nrm.push(...nn.map((x) => x / l));
    }
  }
  for (let i = 0; i < n - 1; i++) for (let j = 0; j < segs; j++) {
    const a = i * segs + j, b = i * segs + ((j + 1) % segs), c = a + segs, e = b + segs;
    tris.push(a, c, b, b, c, e);
  }
  // close the ends with a fan
  for (const [ring, flip] of [[0, true], [n - 1, false]]) {
    for (let j = 1; j < segs - 1; j++) {
      const a = ring * segs, b = ring * segs + j, c = ring * segs + j + 1;
      tris.push(...(flip ? [a, c, b] : [a, b, c]));
    }
  }
  return { pos, nrm, tris };
}

/**
 * The face, at the 1.75 m reference size: eye whites, irises, eyebrows and a
 * gentle smile. A face without brows or a mouth reads as a mask, which scares
 * small children, so every stand-in gets one. Each feature is set onto the skin
 * found by marching the body's field from the front.
 */
function faceOf(sdfRef) {
  const surfaceZ = (x, y) => { let z = 0.2; while (z > -0.05 && sdfRef([x, y, z]) > 0) z -= 0.0005; return z; };
  const normalAt = (p) => {
    const e = 0.001;
    const g = [0, 1, 2].map((a) => { const q = [...p], r = [...p]; q[a] += e; r[a] -= e; return sdfRef(q) - sdfRef(r); });
    const l = Math.hypot(...g) || 1;
    return g.map((x) => x / l);
  };
  const onSkin = (x, y, sink) => [x, y, surfaceZ(x, y) - sink];
  const parts = { whites: [], irises: [], brows: [], mouth: [] };
  for (const x of [1, -1]) {
    const c = [x * 0.031, 1.655, 0.09];
    parts.whites.push(sphere(c, 0.0125, 8, 12));
    // the iris sits on the front of the white, looking a touch inward and down
    parts.irises.push(sphere([c[0] - x * 0.0015, c[1] - 0.001, c[2] + 0.0095], 0.0068, 6, 10));
    // a brow: a soft arch over the eye, thicker near the nose
    const brow = [];
    for (let i = 0; i <= 8; i++) {
      const t = i / 8;
      const bx = x * (0.016 + 0.034 * t);
      brow.push(onSkin(bx, 1.674 + 0.006 * Math.sin(Math.PI * (0.25 + 0.75 * t)) - 0.003 * t, 0.0004));
    }
    parts.brows.push(strip(brow, normalAt, (t) => 0.0034 - 0.0016 * t, 0.0018));
  }
  // the mouth: a shallow smile, its corners lifted
  const mouth = [];
  for (let i = 0; i <= 12; i++) {
    const t = i / 12, mx = -0.019 + 0.038 * t;
    mouth.push(onSkin(mx, 1.574 + 9 * mx * mx, 0.0006));
  }
  parts.mouth.push(strip(mouth, normalAt, (t) => 0.0016 + 0.0012 * Math.sin(Math.PI * t), 0.0014));
  return parts;
}

// ---- moves -----------------------------------------------------------------

const D = Math.PI / 180;
const qAxis = (axis, deg) => {
  const h = (deg * D) / 2, s = Math.sin(h);
  return [axis[0] * s, axis[1] * s, axis[2] * s, Math.cos(h)];
};
const qmul = (a, b) => [
  a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
  a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
  a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
  a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
];
/** Turn about X (pitch: negative swings a hanging limb forward), then Y, then Z, in degrees. */
const rot = (x = 0, y = 0, z = 0) => qmul(qAxis([1, 0, 0], x), qmul(qAxis([0, 1, 0], y), qAxis([0, 0, 1], z)));

/** Arms hanging nearer the body than the A-pose, as people stand. */
const relaxed = (l = {}, r = {}) => ({
  upperarm_l: rot(l.x ?? 0, 0, -22 + (l.z ?? 0)), upperarm_r: rot(r.x ?? 0, 0, 22 - (r.z ?? 0)),
  lowerarm_l: rot(l.fx ?? -8, 0, 0), lowerarm_r: rot(r.fx ?? -8, 0, 0),
});

/** Each move: its length in seconds and the pose at phase f (radians, 0..2π over the move). */
const MOVES = {
  Idle: [3, (f) => ({
    spine_02: rot(1.2 * Math.sin(f)), spine_03: rot(0.8 * Math.sin(f)), head: rot(0, 4 * Math.sin(f)),
    ...relaxed({ z: 1.5 * Math.sin(f) }, { z: 1.5 * Math.sin(f) }),
  })],
  Walk: [1.1, (f) => {
    const leg = (g) => {
      const thigh = -24 * Math.sin(g);
      const knee = 6 + 38 * Math.max(0, Math.cos(g - 0.5)) ** 1.5;
      return [rot(thigh), rot(knee), rot(-(thigh + knee) * 0.35)];
    };
    const [tl, cl, fl] = leg(f), [tr, cr, fr] = leg(f + Math.PI);
    return {
      pelvis: rot(0, 4 * Math.sin(f)), spine_03: rot(2, -6 * Math.sin(f)),
      thigh_l: tl, calf_l: cl, foot_l: fl, thigh_r: tr, calf_r: cr, foot_r: fr,
      ...relaxed({ x: 18 * Math.sin(f), fx: -14 }, { x: -18 * Math.sin(f), fx: -14 }),
      _pelvisY: 0.012 * Math.cos(2 * f),
    };
  }],
  Wave: [2, (f) => ({
    ...relaxed({ z: 1.5 * Math.sin(f) }),
    upperarm_r: rot(-10, 0, -128), lowerarm_r: rot(0, 0, 30 + 22 * Math.sin(3 * f)),
    head: rot(-3, -6, 4), spine_03: rot(0, -4, 2),
  })],
  Talk: [3, (f) => ({
    spine_03: rot(1.5 * Math.sin(f), 3 * Math.sin(f)), head: rot(4 * Math.sin(4 * f), 7 * Math.sin(f)),
    ...relaxed({ x: -6, fx: -38 - 16 * Math.sin(2 * f) }, { x: -8, fx: -30 - 22 * Math.sin(3 * f + 1) }),
  })],
  Sit: [3, (f) => ({
    thigh_l: rot(-88, 4), thigh_r: rot(-88, -4), calf_l: rot(86), calf_r: rot(86), foot_l: rot(2), foot_r: rot(2),
    spine_01: rot(-4), spine_03: rot(4 + 1.2 * Math.sin(f)), head: rot(-4, 5 * Math.sin(f)),
    ...relaxed({ x: -14, z: 6, fx: -58 }, { x: -14, z: 6, fx: -58 }),
    _pelvisY: 0.53 - 0.95,
  })],
};

// ---- the document ----------------------------------------------------------

/** A stand-in body as a glTF document. `build` is a name from BUILDS or a build object. */
export function makeStandin(build = 'female', { moves = true, name } = {}) {
  const b = typeof build === 'string' ? BUILDS[build] : build;
  if (!b) throw new Error(`unknown build "${build}" (try ${Object.keys(BUILDS).join(', ')})`);
  const s = b.height / 1.75;
  const J0 = jointsAt(b);
  const J = Object.fromEntries(Object.entries(J0).map(([k, v]) => [k, v.map((x) => x * s)]));
  const shapes = shapesOf(b, J0);
  const sdfRef = (p) => { let d = Infinity; for (const sh of shapes) d = d === Infinity ? sh.d(p) : smin(d, sh.d(p), sh.k); return d; };
  const sdf = (p) => sdfRef(p.map((x) => x / s)) * s;

  const net = surfaceNets(sdf, [-0.7 * s, -0.03 * s, -0.22 * s], [0.7 * s, 1.8 * s, 0.26 * s], 0.016 * s);
  const vcount = net.pos.length / 3;

  // normals from the field's slope
  const nrm = new Float32Array(net.pos.length);
  const e = 0.002 * s;
  for (let v = 0; v < vcount; v++) {
    const p = [net.pos[v * 3], net.pos[v * 3 + 1], net.pos[v * 3 + 2]];
    const g = [0, 1, 2].map((a) => { const q = [...p], r = [...p]; q[a] += e; r[a] -= e; return sdf(q) - sdf(r); });
    const l = Math.hypot(...g) || 1;
    for (let a = 0; a < 3; a++) nrm[v * 3 + a] = g[a] / l;
  }

  // weights: the bones of the shapes nearest the surface here, but only bones
  // close in the rig to the nearest one, so a thigh never pulls on the other thigh
  const names = BONES.map(([n]) => n);
  const parentOf = Object.fromEntries(BONES);
  const ancestors = (bone, k) => { const out = []; for (let p = parentOf[bone]; p && out.length < k; p = parentOf[p]) out.push(p); return out; };
  const near = (a, c) => a === c || ancestors(a, 2).includes(c) || ancestors(c, 2).includes(a);
  const band = 0.04;
  const joints = new Uint16Array(vcount * 4), weights = new Float32Array(vcount * 4);
  for (let v = 0; v < vcount; v++) {
    const p = [net.pos[v * 3] / s, net.pos[v * 3 + 1] / s, net.pos[v * 3 + 2] / s];
    const d = shapes.map((sh) => sh.d(p));
    let owner = 0;
    for (let i = 1; i < d.length; i++) if (d[i] < d[owner]) owner = i;
    const w = new Map();
    shapes.forEach((sh, i) => {
      if (!near(sh.bone, shapes[owner].bone)) return;
      const x = Math.max(0, 1 - (d[i] - d[owner]) / band) ** 2;
      if (x > 0) w.set(sh.bone, (w.get(sh.bone) ?? 0) + x);
    });
    const top = [...w].sort((a, c) => c[1] - a[1]).slice(0, 4);
    const total = top.reduce((t, [, x]) => t + x, 0);
    top.forEach(([bone, x], k) => { joints[v * 4 + k] = names.indexOf(bone); weights[v * 4 + k] = x / total; });
  }

  const doc = new Document();
  const buf = doc.createBuffer();
  const acc = (type, array) => doc.createAccessor().setType(type).setArray(array).setBuffer(buf);

  // the rig: rest rotations are all identity, so a move's turn is in world axes at rest
  const nodes = {};
  for (const [bone, parent] of BONES) {
    const t = parent ? J[bone].map((x, i) => x - J[parent][i]) : J[bone];
    nodes[bone] = doc.createNode(bone).setTranslation(t);
    if (parent) nodes[parent].addChild(nodes[bone]);
  }
  const ibm = new Float32Array(names.length * 16);
  names.forEach((bone, i) => ibm.set([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -J[bone][0], -J[bone][1], -J[bone][2], 1], i * 16));
  const skin = doc.createSkin('rig').setSkeleton(nodes.root).setInverseBindMatrices(acc('MAT4', ibm));
  for (const bone of names) skin.addJoint(nodes[bone]);

  const mat = (n, hex, rough = 0.85) => doc.createMaterial(n)
    .setBaseColorFactor([...hex.match(/\w\w/g).map((h) => (parseInt(h, 16) / 255) ** 2.2), 1]).setRoughnessFactor(rough).setMetallicFactor(0);
  const body = doc.createPrimitive()
    .setAttribute('POSITION', acc('VEC3', net.pos)).setAttribute('NORMAL', acc('VEC3', nrm))
    .setAttribute('JOINTS_0', acc('VEC4', joints)).setAttribute('WEIGHTS_0', acc('VEC4', weights))
    .setIndices(acc('SCALAR', vcount > 65535 ? net.tris : Uint16Array.from(net.tris)))
    .setMaterial(mat('skin', b.skin));
  // the face, held by the head bone: one primitive per material
  const mesh = doc.createMesh('Body').addPrimitive(body);
  const headJ = names.indexOf('head');
  const face = faceOf(sdfRef);
  const looks = {
    whites: mat('eye_white', '#efece6', 0.35),
    irises: mat('eyes', b.iris ?? '#3b2a1e', 0.25),
    brows: mat('brows', b.brows ?? '#3a2a1e', 0.9),
    mouth: mat('mouth', b.lips ?? lipsFor(b.skin), 0.6),
  };
  for (const [part, pieces] of Object.entries(face)) {
    const g = { pos: [], nrm: [], tris: [] };
    for (const p of pieces) {
      const base = g.pos.length / 3;
      g.pos.push(...p.pos.map((x) => x * s)); g.nrm.push(...p.nrm); g.tris.push(...p.tris.map((t) => t + base));
    }
    const nv = g.pos.length / 3;
    mesh.addPrimitive(doc.createPrimitive()
      .setAttribute('POSITION', acc('VEC3', new Float32Array(g.pos))).setAttribute('NORMAL', acc('VEC3', new Float32Array(g.nrm)))
      .setAttribute('JOINTS_0', acc('VEC4', new Uint16Array(nv * 4).map((_, i) => (i % 4 === 0 ? headJ : 0))))
      .setAttribute('WEIGHTS_0', acc('VEC4', new Float32Array(nv * 4).map((_, i) => (i % 4 === 0 ? 1 : 0))))
      .setIndices(acc('SCALAR', new Uint16Array(g.tris)))
      .setMaterial(looks[part]));
  }
  const bodyNode = doc.createNode('Body').setMesh(mesh).setSkin(skin);
  doc.createScene(name ?? 'standin').addChild(nodes.root).addChild(bodyNode);

  if (moves) for (const [clip, [dur, pose]] of Object.entries(MOVES)) addMove(doc, acc, nodes, J, clip, dur, pose);
  return doc;
}

function addMove(doc, acc, nodes, J, clip, dur, pose) {
  const fps = 20;
  const frames = Math.round(dur * fps) + 1;
  const times = new Float32Array(frames).map((_, i) => (i / (frames - 1)) * dur);
  const poses = [...times].map((t) => pose((t / dur) * 2 * Math.PI));
  const anim = doc.createAnimation(clip);
  const channel = (node, path, type, values) => {
    const sampler = doc.createAnimationSampler().setInput(acc('SCALAR', times)).setOutput(acc(type, values)).setInterpolation('LINEAR');
    anim.addSampler(sampler).addChannel(doc.createAnimationChannel().setTargetNode(node).setTargetPath(path).setSampler(sampler));
  };
  const bones = new Set(poses.flatMap((p) => Object.keys(p)).filter((k) => !k.startsWith('_')));
  for (const bone of bones) {
    const q = new Float32Array(frames * 4);
    poses.forEach((p, i) => q.set(p[bone] ?? [0, 0, 0, 1], i * 4));
    channel(nodes[bone], 'rotation', 'VEC4', q);
  }
  if (poses.some((p) => p._pelvisY)) {
    const rest = nodes.pelvis.getTranslation();
    const s = J.pelvis[1] / 0.95;
    const t = new Float32Array(frames * 3);
    poses.forEach((p, i) => t.set([rest[0], rest[1] + (p._pelvisY ?? 0) * s, rest[2]], i * 3));
    channel(nodes.pelvis, 'translation', 'VEC3', t);
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const out = join(HERE, 'out');
  mkdirSync(out, { recursive: true });
  const io = new NodeIO();
  for (const build of process.argv.length > 2 ? process.argv.slice(2) : Object.keys(BUILDS)) {
    const doc = makeStandin(build);
    const file = join(out, `standin_${build}.glb`);
    await io.write(file, doc);
    const tris = doc.getRoot().listMeshes()[0].listPrimitives()[0].getIndices().getCount() / 3;
    console.log(`🧍 ${file} (${tris} triangles)`);
  }
}
