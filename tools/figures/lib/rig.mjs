// Reads a rigged body out of a glTF document so clothes can be made from it:
// what each bone is (by its name, so UE5/Quaternius, Mixamo, Blender ".L" and
// our own stand-in all work), where the joints are, and for every vertex which
// part of the body it's on and how far down an arm or leg it is.

/** Bone roles, checked in this order: the first pattern a name matches wins. */
const ROLES = [
  ['finger', /thumb|index|middle|ring|pinky|finger/i],
  ['hand', /hand|wrist/i],
  ['toe', /ball|toe/i],
  ['foot', /foot|ankle/i],
  ['thigh', /thigh|upleg|upperleg/i],
  ['calf', /calf|shin|knee|lowerleg|(left|right)leg$/i],
  ['lowerarm', /lowerarm|forearm|elbow/i],
  ['upperarm', /upperarm|(left|right)arm$|^arm/i],
  ['clavicle', /clavicle|shoulder|collar/i],
  ['neck', /neck/i],
  ['head', /head|jaw|eye|brow|lip|ear|nose|cheek/i],
  ['spine', /spine|chest|torso|abdomen|belly/i],
  ['pelvis', /pelvis|hip/i],
];

export function boneRole(rawName) {
  const name = rawName.replace(/^mixamorig\d*:/i, '');
  const role = ROLES.find(([, re]) => re.test(name))?.[0] ?? 'other';
  const side = /([_.\- ]l$|^l[_.]|left|_l_)/i.test(name) ? 'l' : /([_.\- ]r$|^r[_.]|right|_r_)/i.test(name) ? 'r' : 'c';
  return { role, side };
}

export const ARM = new Set(['upperarm', 'lowerarm']);
export const LEG = new Set(['thigh', 'calf']);

// small vector helpers on [x, y, z]
export const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
export const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
export const scale = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
export const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
export const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
export const len = (a) => Math.sqrt(dot(a, a));
export const norm = (a) => scale(a, 1 / (len(a) || 1));
const avg = (ps) => scale(ps.reduce(add, [0, 0, 0]), 1 / ps.length);

/** Where an inverse bind matrix puts its joint: the translation of its inverse (affine only). */
function jointPosition(m) {
  // the 3x3 part's inverse times −translation
  const [a, b, c, d, e, f, g, h, i] = [m[0], m[4], m[8], m[1], m[5], m[9], m[2], m[6], m[10]];
  const det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
  const inv = [
    (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
    (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
    (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det,
  ];
  const t = [-m[12], -m[13], -m[14]];
  return [0, 1, 2].map((r) => inv[r * 3] * t[0] + inv[r * 3 + 1] * t[1] + inv[r * 3 + 2] * t[2]);
}

function attr(prim, name, size, fallback) {
  const acc = prim.getAttribute(name);
  const n = prim.getAttribute('POSITION').getCount();
  const out = new Float32Array(n * size);
  if (!acc) { if (fallback) out.fill(fallback); return out; }
  const el = [];
  for (let i = 0; i < n; i++) { acc.getElement(i, el); for (let k = 0; k < size; k++) out[i * size + k] = el[k]; }
  return out;
}

/** The point on segment a→b nearest p: [t 0..1, distance]. */
function onSegment(p, a, b) {
  const ab = sub(b, a);
  const t = Math.max(0, Math.min(1, dot(sub(p, a), ab) / (dot(ab, ab) || 1)));
  return [t, len(sub(p, add(a, scale(ab, t))))];
}

/** How far along a chain of three joints p is: 0 at the first, 1 at the second, 2 at the third. */
function chainT(p, pts) {
  const [t0, d0] = onSegment(p, pts[0], pts[1]);
  const [t1, d1] = onSegment(p, pts[1], pts[2]);
  return d0 <= d1 ? t0 : 1 + t1;
}

/**
 * The body: the skinned mesh with the most vertices, its skin's joints, and
 * per-vertex facts for every primitive. Throws if there's no skinned mesh.
 */
export function readBody(doc) {
  const root = doc.getRoot();
  let best = null;
  for (const node of root.listNodes()) {
    const mesh = node.getMesh();
    if (!mesh || !node.getSkin()) continue;
    const verts = mesh.listPrimitives().reduce((n, p) => n + p.getAttribute('POSITION').getCount(), 0);
    if (!best || verts > best.verts) best = { node, verts };
  }
  if (!best) throw new Error('no skinned mesh: the body needs a rig');
  const { node } = best;
  const skin = node.getSkin();
  const ibm = skin.getInverseBindMatrices();
  const depthOf = (n) => { let d = 0; for (let p = n.getParentNode(); p; p = p.getParentNode()) d++; return d; };
  const joints = skin.listJoints().map((j, i) => {
    const m = ibm ? ibm.getElement(i, []) : [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    return { node: j, name: j.getName(), ...boneRole(j.getName()), depth: depthOf(j), pos: jointPosition(m) };
  });
  // the main joint of a role on a side: the one nearest the root (twist bones are deeper)
  const main = (role, side = 'c') => joints.filter((j) => j.role === role && (side === 'c' || j.side === side))
    .sort((a, b) => a.depth - b.depth)[0];
  const need = (role, side = 'c') => {
    const j = main(role, side);
    if (!j) throw new Error(`no ${role}${side !== 'c' ? '_' + side : ''} bone; the bone names are: ${joints.map((x) => x.name).join(', ')}`);
    return j.pos;
  };

  const prims = node.getMesh().listPrimitives().filter((p) => p.getMode() === 4 && p.getAttribute('JOINTS_0')).map((prim) => {
    const pos = attr(prim, 'POSITION', 3);
    const n = pos.length / 3;
    const idxAcc = prim.getIndices();
    return {
      prim, pos,
      nrm: attr(prim, 'NORMAL', 3),
      joints: attr(prim, 'JOINTS_0', 4),
      weights: attr(prim, 'WEIGHTS_0', 4),
      idx: idxAcc ? Uint32Array.from(idxAcc.getArray()) : Uint32Array.from({ length: n }, (_, i) => i),
    };
  });

  const sides = ['l', 'r'];
  const arm = Object.fromEntries(sides.map((s) => [s, [need('upperarm', s), need('lowerarm', s), need('hand', s)]]));
  const leg = Object.fromEntries(sides.map((s) => [s, [need('thigh', s), need('calf', s), need('foot', s)]]));
  const pelvis = (main('pelvis') ?? main('spine')).pos;
  const neck = (main('neck') ?? main('head')).pos;
  const head = need('head');
  const up = norm(sub(head, avg([leg.l[2], leg.r[2]])));
  let left = sub(avg([arm.l[0], leg.l[0]]), avg([arm.r[0], leg.r[0]]));
  left = norm(sub(left, scale(up, dot(left, up))));
  const forward = cross(left, up);
  let floor = Infinity, top = -Infinity;
  for (const p of prims) for (let i = 0; i < p.pos.length; i += 3) {
    const y = p.pos[i] * up[0] + p.pos[i + 1] * up[1] + p.pos[i + 2] * up[2];
    floor = Math.min(floor, y); top = Math.max(top, y);
  }
  const H = top - floor;
  const height = (p) => dot(p, up) - floor;
  const toe = (s) => main('toe', s)?.pos ?? add(leg[s][2], scale(forward, 0.08 * H));

  // the axis each part of the body wraps around, for cloth patterns (wardrobe.mjs)
  const axes = { torso: [pelvis, neck], head: [head, add(head, scale(up, 0.15 * H))] };
  for (const s of sides) {
    // a whole arm or leg wraps round one axis, so a pattern runs on past the elbow or knee
    axes['arm_' + s] = [arm[s][0], arm[s][2]];
    axes['hd_' + s] = [arm[s][2], add(arm[s][2], scale(sub(arm[s][2], arm[s][1]), 0.7))];
    axes['leg_' + s] = [leg[s][0], leg[s][2]];
    axes['ft_' + s] = [leg[s][2], toe(s)];
  }

  /** Facts about one vertex, from its rest position and its four joints and weights. */
  function vertex(prim, v) {
    const p = [prim.pos[v * 3], prim.pos[v * 3 + 1], prim.pos[v * 3 + 2]];
    const byRole = new Map();
    for (let k = 0; k < 4; k++) {
      const w = prim.weights[v * 4 + k];
      if (w <= 0) continue;
      const j = joints[prim.joints[v * 4 + k]];
      const key = j.role + ':' + j.side;
      byRole.set(key, (byRole.get(key) ?? 0) + w);
    }
    let key = 'other:c', bw = -1;
    for (const [k, w] of byRole) if (w > bw) { bw = w; key = k; }
    let [role, side] = key.split(':');
    // a limb bone without a side (rare) takes the side the vertex is on
    if (side === 'c' && (ARM.has(role) || LEG.has(role) || ['hand', 'finger', 'foot', 'toe'].includes(role))) side = dot(sub(p, pelvis), left) > 0 ? 'l' : 'r';
    const armT = side !== 'c' ? chainT(p, arm[side]) : 0;
    const legT = side !== 'c' ? chainT(p, leg[side]) : 0;
    let axis = 'torso';
    if (ARM.has(role)) axis = 'arm_' + side;
    else if (role === 'hand' || role === 'finger') axis = 'hd_' + side;
    else if (LEG.has(role)) axis = 'leg_' + side;
    else if (role === 'foot' || role === 'toe') axis = 'ft_' + side;
    else if (role === 'head') axis = 'head';
    return {
      p, role, side, armT, legT, axis,
      y: height(p),
      lateral: dot(sub(p, neck), left),
      front: dot(sub(p, neck), forward),
    };
  }

  return {
    node, skin, joints, prims, up, left, forward, floor, H, axes, vertex, height,
    land: {
      pelvis: height(pelvis), neck: height(neck), head: height(head), top: H,
      knee: (height(leg.l[1]) + height(leg.r[1])) / 2,
      upperArm: len(sub(arm.l[1], arm.l[0])), thigh: len(sub(leg.l[1], leg.l[0])), calf: len(sub(leg.l[2], leg.l[1])),
      headCentre: add(head, scale(up, 0.45 * (H - height(head)))),
    },
  };
}
