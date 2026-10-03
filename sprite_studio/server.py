import http.server, socketserver, sys, json, base64, os, io, re, urllib.request, urllib.parse, random, subprocess, threading
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageSequence

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'autogen'))
import pixelkit  # noqa: E402  (same GIF writer as autogen: shared palette, index 0 transparent)
import effects  # noqa: E402  (attack effects: the Attacks tab lists effects.MOVES)
import looks  # noqa: E402  (which look each row is on: what RECOLOUR steps through)

PORT = int(os.environ.get("STUDIO_PORT", 8080))
SAVE_DIR = "../rhc-android/app/src/main/res/drawable-nodpi/"
AUDIO_DIR = "../rhc-android/app/src/main/res/raw/"
MODELS_FILE = "../rhc-android/app/src/main/java/com/rockhard/blocker/GameModels.kt"
HAND_EDITS = "autogen/hand_edits.txt"
AUTOGEN = "autogen/autogen.py"
regen_lock = threading.Lock()  # one autogen run at a time: two would write the same GIFs
BASE_EFFECTS = ["laser", "net", "sword_of_the_spirit"]  # shared effects that aren't one move's (net is the thrown item)


def is_2x(frames):
    """True when every 2x2 block of every frame is one colour (autogen writes at 2x)."""
    for f in frames:
        if f.width % 2 or f.height % 2:
            return False
        px = f.load()
        for y in range(0, f.height, 2):
            for x in range(0, f.width, 2):
                c = px[x, y]
                if not c[3]:
                    c = None
                for dx, dy in ((1, 0), (0, 1), (1, 1)):
                    d = px[x + dx, y + dy]
                    if (d if d[3] else None) != c:
                        return False
    return True


def load_gif(path):
    """Frames at their real pixel size, per-frame durations, and the write scale."""
    img = Image.open(path)
    frames, durations = [], []
    for frame in ImageSequence.Iterator(img):
        frames.append(frame.convert("RGBA"))
        durations.append(frame.info.get('duration', 125) or 125)
    scale = 2 if frames and is_2x(frames) else 1
    if scale == 2:
        frames = [f.resize((f.width // 2, f.height // 2), Image.Resampling.NEAREST) for f in frames]
    return frames, durations, scale


def hand_edits():
    """GIF names saved from the Studio, which autogen.py skips unless --force."""
    if not os.path.exists(HAND_EDITS): return set()
    with open(HAND_EDITS) as f: return {l.strip() for l in f if l.strip() and not l.startswith('#')}


def note_hand_edit(filename):
    if filename not in hand_edits():
        with open(HAND_EDITS, 'a') as f: f.write(filename + "\n")


def forget_hand_edits(names):
    """Drop names from hand_edits.txt (autogen has just redrawn them), keeping comments."""
    if not os.path.exists(HAND_EDITS): return
    with open(HAND_EDITS) as f: lines = f.readlines()
    with open(HAND_EDITS, 'w') as f: f.writelines(l for l in lines if l.strip() not in names)


def row_files(beast, effect=False):
    """The GIF names in a matrix row (what /dashboard_data checks for)."""
    if effect: return {f"fx_{beast}.gif"}
    return {f"fx_{beast}.gif" if anim == 'fx' else f"spr_{beast}_{anim}.gif" for anim in ANIMATIONS}


def look_info(row, has_looks, current):
    """What the dashboard shows of a row's look: its number, and the armour tone for a creature."""
    n = current.get(row, 0) if has_looks else 0
    tone = looks.describe(row, n)[0] if n and row not in effects.EFFECTS else ''
    return {'has_looks': has_looks, 'look': n, 'look_name': tone}


def attack_rows():
    """The Attacks tab: one fx GIF per move (effects.MOVES), then the base effects.
    Re-imports effects.py so a new move added there shows up without a restart."""
    import importlib
    importlib.reload(effects)
    painted = hand_edits()
    rows = [{'beast': effects.slug(m), 'move': m, 'family': fam, 'line': col} for m, (fam, col, _) in effects.MOVES.items()]
    rows += [{'beast': b, 'move': f'({b})', 'family': 'base', 'line': ''} for b in BASE_EFFECTS]
    current = looks.load()
    for r in rows:
        f = f"fx_{r['beast']}.gif"
        r.update(file=f, exists=os.path.exists(os.path.join(SAVE_DIR, f)), hand_edited=[f] if f in painted else [])
        r.update(look_info(r['beast'], r['beast'] in effects.HAS_LOOKS, current))
    return rows


def regenerate(beast, force, look=None):
    """Redraw one row (or a comma list of rows) with autogen.py. A fresh process, so edits to designs.py are picked up.
    look ('next', 'prev' or 'original') moves the rows to another look first (autogen/looks.py); None redraws them as they are."""
    cmd = [sys.executable, AUTOGEN, '--only', beast] + (['--force'] if force else []) + (['--look', look] if look else [])
    print(f"🔄 Regenerating {beast}{' (--force)' if force else ''}{f' (--look {look})' if look else ''}...", flush=True)
    with regen_lock:
        run = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', timeout=600,
                             env=dict(os.environ, PYTHONIOENCODING='utf-8'))
    log = (run.stdout + run.stderr).strip()
    if run.returncode != 0:
        return {'status': 'error', 'error': log.splitlines()[-1] if log else f'autogen exited {run.returncode}', 'log': log}
    if force: forget_hand_edits(set().union(*(row_files(b) | row_files(b, effect=True) for b in beast.split(','))))
    wrote = re.search(r'Wrote (\d+) GIFs', log)
    return {'status': 'success', 'written': int(wrote.group(1)) if wrote else 0, 'log': log,
            'kept': [l.split()[1] for l in log.splitlines() if l.startswith('✋')],
            'warnings': [l for l in log.splitlines() if l.startswith('⚠️')]}

# ADDED FRONT FACING ANIMATIONS
ANIMATIONS =['idle', 'attack', 'hit', 'evade', 'faint', 'victory', 'explore', 'fx', 'walk_front', 'attack_front']
# 3D Wilds views from autogen (3/4 front, 3/4 back, back) and the full spin.
# Rows that don't draw those views yet just show CREATE in these columns.
ANIMATIONS += ['idle_front', 'walk_fq', 'idle_fq', 'walk_bq', 'idle_bq', 'walk_back', 'idle_back', 'turn']

class SpriteHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        parsed_url = urlparse(self.path)
        if parsed_url.path == '/dashboard_data':
            beasts =["player", "poacher", "aegis", "titan"]
            if os.path.exists(MODELS_FILE):
                with open(MODELS_FILE, 'r') as f: beasts.extend([b.lower().replace(" ", "_") for b in re.findall(r'BeastDef\("([^"]+)"', f.read())])
            matrix =[]
            painted = hand_edits()
            current = looks.load()
            for beast in sorted(list(set(beasts))):
                row = {'beast': beast, 'anims': {}, 'hand_edited': sorted(row_files(beast) & painted)}
                row.update(look_info(beast, beast not in looks.PLAIN_ROWS, current))
                for anim in ANIMATIONS:
                    filename = f"spr_{beast}_{anim}.gif"
                    if anim == 'fx': filename = f"fx_{beast}.gif"
                    row['anims'][anim] = {'exists': os.path.exists(os.path.join(SAVE_DIR, filename)), 'file': filename}
                matrix.append(row)
            self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({'matrix': matrix, 'animations': ANIMATIONS, 'attacks': attack_rows()}).encode('utf-8'))
        elif parsed_url.path.startswith('/drawable/'):
            filepath = os.path.join(SAVE_DIR, parsed_url.path.replace('/drawable/', ''))
            if os.path.exists(filepath):
                self.send_response(200); self.send_header('Content-type', 'image/gif'); self.end_headers()
                with open(filepath, 'rb') as f: self.wfile.write(f.read())
            else: self.send_response(404); self.end_headers()
        elif parsed_url.path == '/load':
            qs = parse_qs(parsed_url.query); name = os.path.basename(qs.get('file',[''])[0])
            filepath = os.path.join(SAVE_DIR, name)
            if not name or not os.path.exists(filepath):
                self.send_json({'error': f'{name or "no file"} not found'}, 404); return
            try:
                frames, durations, scale = load_gif(filepath)
            except Exception as e:
                self.send_json({'error': f'{name}: {e}'}, 500); return
            urls = []
            for frame in frames:
                b = io.BytesIO(); frame.save(b, format="PNG"); urls.append("data:image/png;base64," + base64.b64encode(b.getvalue()).decode('utf-8'))
            self.send_json({'frames': urls, 'width': frames[0].width if frames else 0, 'durations': durations, 'scale': scale})
        else: super().do_GET()

    def do_POST(self):
        if self.path == '/generate':
            data = json.loads(self.rfile.read(int(self.headers['Content-Length'])).decode('utf-8'))
            prompt = data['prompt'] + " facing right, side profile, 8-bit pixel art, video game sprite, clean white background, isolated"
            seed = data.get('seed')
            if not seed: seed = str(random.randint(1, 999999))
            width = int(data.get('width', 64))
            
            url = f"https://image.pollinations.ai/prompt/{urllib.parse.quote(prompt)}?width=256&height=256&nologo=true&seed={seed}"
            try:
                req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
                with urllib.request.urlopen(req, timeout=15) as response: img_data = response.read()
                img = Image.open(io.BytesIO(img_data)).convert("RGBA").resize((width, width), Image.NEAREST)
                pixels = img.load()
                for y in range(img.height):
                    for x in range(img.width):
                        r, g, b, a = pixels[x, y]
                        if r > 230 and g > 230 and b > 230: pixels[x, y] = (255, 255, 255, 0)
                b = io.BytesIO(); img.save(b, format="PNG")
                self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
                self.wfile.write(json.dumps({'status': 'success', 'image': "data:image/png;base64," + base64.b64encode(b.getvalue()).decode('utf-8'), 'seed': seed}).encode('utf-8'))
            except Exception as e: self.send_response(500); self.end_headers()
            
        elif self.path == '/save_audio':
            data = json.loads(self.rfile.read(int(self.headers['Content-Length'])).decode('utf-8'))
            filename = data['filename'].replace(" ", "_").replace("-", "_").lower().replace(".gif", "") + ".wav"
            if not os.path.exists(AUDIO_DIR): os.makedirs(AUDIO_DIR)
            with open(os.path.join(AUDIO_DIR, filename), "wb") as fh: fh.write(base64.b64decode(data['audio']))
            self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({'status': 'success', 'file': filename}).encode('utf-8'))

        elif self.path == '/save':
            data = json.loads(self.rfile.read(int(self.headers['Content-Length'])).decode('utf-8'))
            filename = os.path.basename(data['filename'].replace(" ", "_").lower())
            if not filename.endswith(".gif"): filename += ".gif"
            frames =[Image.open(io.BytesIO(base64.b64decode(b64.split(",")[1]))).convert("RGBA") for b64 in data['frames']]
            if not frames:
                self.send_json({'status': 'error', 'error': 'no frames'}, 400); return
            default = int(data.get('duration', 125))  # 8 FPS
            durations = [int(d or default) for d in data.get('durations', [])][:len(frames)]
            durations += [default] * (len(frames) - len(durations))
            scale = max(1, int(data.get('scale', 1)))
            try:
                pixelkit.save_gif(frames, durations, os.path.join(SAVE_DIR, filename), scale=scale)
            except ValueError as e:  # more than 255 colours
                self.send_json({'status': 'error', 'error': str(e)}, 400); return
            note_hand_edit(filename)
            self.send_json({'status': 'success', 'file': filename, 'scale': scale})

        elif self.path == '/regenerate':
            data = json.loads(self.rfile.read(int(self.headers['Content-Length'])).decode('utf-8'))
            beast = str(data.get('beast', '')).lower()
            if not re.fullmatch(r'[a-z0-9_]+(,[a-z0-9_]+)*', beast):
                self.send_json({'status': 'error', 'error': f'bad row name {beast!r}'}, 400); return
            look = data.get('look') or None
            if look not in (None, 'next', 'prev', 'original'):
                self.send_json({'status': 'error', 'error': f'bad look {look!r}'}, 400); return
            try:
                result = regenerate(beast, bool(data.get('force')), look)
            except subprocess.TimeoutExpired:
                result = {'status': 'error', 'error': 'autogen took over 10 minutes'}
            self.send_json(result, 200 if result['status'] == 'success' else 500)

    def send_json(self, obj, code=200):
        self.send_response(code); self.send_header('Content-type', 'application/json'); self.end_headers()
        self.wfile.write(json.dumps(obj).encode('utf-8'))

os.chdir(os.path.dirname(os.path.abspath(__file__)))
# Threaded: the dashboard requests ~230 GIFs at once, and through the
# Codespaces port forward one stalled connection would otherwise block
# every request behind it (the grid then shows broken images).
http.server.ThreadingHTTPServer.allow_reuse_address = True
http.server.ThreadingHTTPServer.daemon_threads = True
SpriteHandler.timeout = 30  # drop connections that go quiet
with http.server.ThreadingHTTPServer(("", PORT), SpriteHandler) as httpd:
    print(f"🎨 RHC Studio V9 running at http://localhost:{PORT}")
    httpd.serve_forever()
