#!/usr/bin/env python3
"""앱의 지도 미리보기와 같은 OSM 타일·좌표로 마커 위치 PNG를 그린다(에뮬레이터가 없어 대신 쓰는 캡처).
입력: stdin에 'LIVE_GEO <주소> -> Found(point=GeoPoint(lat=.., lon=.., exact=.., query=..))' 줄들(GeocodeRepositoryTest 출력).
"""
import io, math, re, sys, time, urllib.request
from PIL import Image, ImageDraw, ImageFont

UA = "chungyak-radar-dev/0.6 (github.com/Dirtfy/chungyak-advisor; docs preview)"
W, H = 640, 400

def tile(z, x, y):
    req = urllib.request.Request(f"https://tile.openstreetmap.org/{z}/{x}/{y}.png", headers={"User-Agent": UA})
    time.sleep(0.2)
    return Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=20).read())).convert("RGB")

def render(lat, lon, z, title, out):
    n = 2 ** z
    px = (lon + 180) / 360 * n * 256
    py = (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n * 256
    x0, y0 = px - W / 2, py - H / 2
    img = Image.new("RGB", (W, H))
    for tx in range(int(x0 // 256), int((x0 + W) // 256) + 1):
        for ty in range(int(y0 // 256), int((y0 + H) // 256) + 1):
            img.paste(tile(z, tx, ty), (int(tx * 256 - x0), int(ty * 256 - y0)))
    d = ImageDraw.Draw(img)
    cx, cy = W / 2, H / 2
    d.polygon([(cx, cy), (cx - 11, cy - 22), (cx + 11, cy - 22)], fill=(200, 30, 30))
    d.ellipse([cx - 13, cy - 38, cx + 13, cy - 12], fill=(200, 30, 30), outline=(255, 255, 255), width=2)
    try:
        f = ImageFont.truetype("/home/acompany/.fonts/NanumGothic.ttf", 15)
    except OSError:
        f = ImageFont.load_default()
    d.rectangle([0, 0, W, 24], fill=(255, 255, 255))
    d.text((6, 4), title, fill=(0, 0, 0), font=f)
    d.text((W - 190, H - 16), "© OpenStreetMap contributors", fill=(60, 60, 60))
    img.save(out)

pat = re.compile(r"LIVE_GEO (.+?) -> Found\(point=GeoPoint\(lat=([\d.]+), lon=([\d.]+), exact=(\w+), query=(.+?)\)\)")
n = 0
for line in sys.stdin:
    m = pat.search(line)
    if not m:
        continue
    n += 1
    addr, lat, lon, exact, q = m.groups()
    z = 16 if exact == "true" else 14
    out = f"{sys.argv[1]}/map_{n:02d}.png"
    render(float(lat), float(lon), z, f"{q} ({'지번' if exact == 'true' else '대략'}) {float(lat):.5f},{float(lon):.5f}", out)
    print(out, addr)
