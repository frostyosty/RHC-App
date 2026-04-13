import http.server, socketserver, json, base64, os, io, re, urllib.request, urllib.parse, random
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageSequence

PORT = 8080
SAVE_DIR = "../../RockHardBlocker/app/src/main/res/drawable-nodpi/"
AUDIO_DIR = "../../RockHardBlocker/app/src/main/res/raw/"
MODELS_FILE = "../../RockHardBlocker/app/src/main/java/com/rockhard/blocker/GameModels.kt"

ANIMATIONS =['idle', 'attack', 'hit', 'evade', 'faint', 'victory', 'explore', 'fx']

class SpriteHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        parsed_url = urlparse(self.path)
        if parsed_url.path == '/dashboard_data':
            beasts =["player", "poacher", "aegis", "titan", "laser", "bite", "net"]
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
            self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({'matrix': matrix, 'animations': ANIMATIONS}).encode('utf-8'))
        elif parsed_url.path.startswith('/drawable/'):
            filepath = os.path.join(SAVE_DIR, parsed_url.path.replace('/drawable/', ''))
            if os.path.exists(filepath):
                self.send_response(200); self.send_header('Content-type', 'image/gif'); self.end_headers()
                with open(filepath, 'rb') as f: self.wfile.write(f.read())
            else: self.send_response(404); self.end_headers()
        elif parsed_url.path == '/load':
            qs = parse_qs(parsed_url.query); filepath = os.path.join(SAVE_DIR, qs.get('file',[''])[0])
            frames =[]; width = 64
            if os.path.exists(filepath):
                img = Image.open(filepath); width, _ = img.size
                for frame in ImageSequence.Iterator(img):
                    b = io.BytesIO(); frame.convert("RGBA").save(b, format="PNG"); frames.append("data:image/png;base64," + base64.b64encode(b.getvalue()).decode('utf-8'))
            self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({'frames': frames, 'width': width}).encode('utf-8'))
        else: super().do_GET()

    def do_POST(self):
        if self.path == '/generate':
            data = json.loads(self.rfile.read(int(self.headers['Content-Length'])).decode('utf-8'))
            prompt = data['prompt'] + " facing right, side profile, 8-bit pixel art, video game sprite, clean white background, isolated"
            seed = data.get('seed')
            if not seed: seed = str(random.randint(1, 999999))
            width = int(data.get('width', 64))
            
            url = f"https://image.pollinations.ai/prompt/{urllib.parse.quote(prompt)}?width=256&height=256&nologo=true&seed={seed}"
            print(f"🤖 Calling AI (Seed: {seed}): {url}")
            try:
                # ADDED 15 SECOND TIMEOUT SO IT DOES NOT HANG
                req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
                with urllib.request.urlopen(req, timeout=15) as response: 
                    img_data = response.read()
                    
                img = Image.open(io.BytesIO(img_data)).convert("RGBA").resize((width, width), Image.NEAREST)
                
                # FIXED: Pillow getdata() deprecation! Now uses memory-safe pixel access.
                pixels = img.load()
                for y in range(img.height):
                    for x in range(img.width):
                        r, g, b, a = pixels[x, y]
                        if r > 230 and g > 230 and b > 230:
                            pixels[x, y] = (255, 255, 255, 0)
                            
                b = io.BytesIO(); img.save(b, format="PNG")
                self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
                self.wfile.write(json.dumps({'status': 'success', 'image': "data:image/png;base64," + base64.b64encode(b.getvalue()).decode('utf-8'), 'seed': seed}).encode('utf-8'))
            except Exception as e: 
                print("AI ERROR:", e)
                self.send_response(500); self.end_headers()
            
        elif self.path == '/save_audio':
            data = json.loads(self.rfile.read(int(self.headers['Content-Length'])).decode('utf-8'))
            filename = data['filename'].replace(" ", "_").replace("-", "_").lower().replace(".gif", "") + ".wav"
            if not os.path.exists(AUDIO_DIR): os.makedirs(AUDIO_DIR)
            with open(os.path.join(AUDIO_DIR, filename), "wb") as fh: fh.write(base64.b64decode(data['audio']))
            self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({'status': 'success', 'file': filename}).encode('utf-8'))

        elif self.path == '/save':
            data = json.loads(self.rfile.read(int(self.headers['Content-Length'])).decode('utf-8'))
            filename = data['filename'].replace(" ", "_").lower()
            if not filename.endswith(".gif"): filename += ".gif"
            frames =[Image.open(io.BytesIO(base64.b64decode(b64.split(",")[1]))).convert("RGBA") for b64 in data['frames']]
            if len(frames) > 0: frames[0].save(os.path.join(SAVE_DIR, filename), save_all=True, append_images=frames[1:], duration=200, loop=0, disposal=2, transparency=0)
            self.send_response(200); self.send_header('Content-type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({'status': 'success', 'file': filename}).encode('utf-8'))

os.chdir(os.path.dirname(os.path.abspath(__file__)))
socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("", PORT), SpriteHandler) as httpd:
    print(f"🎨 RHC Studio V8.1 running at http://localhost:{PORT}")
    httpd.serve_forever()