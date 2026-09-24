import http.server, socketserver, sys, json, base64, os, io, re, urllib.request, urllib.parse, random
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageSequence

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'autogen'))
import pixelkit  # noqa: E402  (same GIF writer as autogen: shared palette, index 0 transparent)

PORT = int(os.environ.get("STUDIO_PORT", 8080))
SAVE_DIR = "../rhc-android/app/src/main/res/drawable-nodpi/"
AUDIO_DIR = "../rhc-android/app/src/main/res/raw/"
MODELS_FILE = "../rhc-android/app/src/main/java/com/rockhard/blocker/GameModels.kt"
HAND_EDITS = "autogen/hand_edits.txt"
EFFECTS = ["laser", "bite", "net"]  # autogen/effects.py EFFECTS  # autogen.py skips these unless --force


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


def note_hand_edit(filename):
    listed = set()
    if os.path.exists(HAND_EDITS):
        with open(HAND_EDITS) as f: listed = {l.strip() for l in f if l.strip() and not l.startswith('#')}
    if filename not in listed:
        with open(HAND_EDITS, 'a') as f: f.write(filename + "\n")

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
            for beast in sorted(list(set(beasts))):
                row = {'beast': beast, 'anims': {}}
                for anim in ANIMATIONS:
                    filename = f"spr_{beast}_{anim}.gif"
                    if anim == 'fx': filename = f"fx_{beast}.gif"
                    row['anims'][anim] = {'exists': os.path.exists(os.path.join(SAVE_DIR, filename)), 'file': filename}
                matrix.append(row)
            # attack effects (autogen/effects.py): one fx GIF each, no creature animations
            for fx in EFFECTS:
                matrix.append({'beast': fx, 'effect': True, 'anims': {'fx': {'exists': os.path.exists(os.path.join(SAVE_DIR, f"fx_{fx}.gif")), 'file': f"fx_{fx}.gif"}}})
            self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({'matrix': matrix, 'animations': ANIMATIONS}).encode('utf-8'))
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
