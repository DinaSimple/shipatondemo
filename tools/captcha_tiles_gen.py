# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
import random, os, json, secrets, io
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
random.seed()   # public copy: random seed, so published tiles never match production
F = ImageFont.truetype('/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf', 109)
CATS = {
 'cars': '🚗🚙🚕🚓🏎🚘🚖🚐',
 'bicycles': '🚲🚴🚵',
 'traffic lights': '🚦🚥',
 'trees': '🌳🌲🌴🎄',
 'cats': '🐱🐈😺😸',
 'dogs': '🐶🐕🐩🦮',
 'boats': '⛵🚤🛥🚢⛴',
 'chairs': '🪑🛋',
 'shoes': '👟👞👠🥾👢',
 'books': '📚📖📕📗📘',
}
NEUTRAL = '☁⭐🌼🍃🎈🧱🪨🌸🍂💧🔷🔶'
def glyph(ch):
    im = Image.new('RGBA', (160,160), (0,0,0,0)); d = ImageDraw.Draw(im)
    d.text((10,10), ch, font=F, embedded_color=True)
    bb = im.getbbox(); return im.crop(bb) if bb else im
def bg(S):
    c1 = tuple(random.randint(90,235) for _ in range(3)); c2 = tuple(random.randint(90,235) for _ in range(3))
    im = Image.new('RGB', (S,S)); px = im.load()
    for y in range(S):
        t = y/S; c = tuple(int(c1[i]*(1-t)+c2[i]*t) for i in range(3))
        for x in range(S): px[x,y] = c
    d = ImageDraw.Draw(im)
    for _ in range(random.randint(3,7)):
        d.line([(random.randint(0,S),random.randint(0,S)) for _ in range(2)], fill=tuple(random.randint(0,255) for _ in range(3)), width=random.randint(1,3))
    return im
def paste(im, g, scale, S):
    k = S*scale/max(g.width,g.height); g = g.resize((max(1,int(g.width*k)), max(1,int(g.height*k))))
    if random.random()<0.5: g = g.transpose(Image.FLIP_LEFT_RIGHT)
    g = g.rotate(random.uniform(-28,28), expand=True, resample=Image.BICUBIC)
    x = random.randint(-g.width//6, max(-g.width//6, S-g.width*5//6)); y = random.randint(-g.height//6, max(-g.height//6, S-g.height*5//6))
    im.paste(g, (x,y), g)
def tile(main):
    S = 144; im = bg(S)
    for ch in random.sample(NEUTRAL, random.randint(1,3)): paste(im, glyph(ch), random.uniform(0.18,0.32), S)
    paste(im, glyph(main), random.uniform(0.45,0.72), S)
    if random.random()<0.5: paste(im, glyph(random.choice(NEUTRAL)), random.uniform(0.15,0.25), S)
    im = ImageEnhance.Color(im).enhance(random.uniform(0.6,1.3))
    im = ImageEnhance.Brightness(im).enhance(random.uniform(0.85,1.1))
    px = im.load()
    for _ in range(900):
        x,y = random.randrange(S), random.randrange(S); v = random.randint(-45,45)
        r,g,b = px[x,y]; px[x,y] = (max(0,min(255,r+v)),max(0,min(255,g+v)),max(0,min(255,b+v)))
    if random.random()<0.4: im = im.filter(ImageFilter.GaussianBlur(0.6))
    return im
os.makedirs('out', exist_ok=True)
rows = []
for label, chars in CATS.items():
    for i in range(18):
        tid = secrets.token_hex(6)
        tile(chars[i % len(chars)]).save(f'out/{tid}.jpg', quality=72, optimize=True)
        rows.append((tid, label))
json.dump(rows, open('tiles.json','w'))
print(len(rows), sum(os.path.getsize('out/'+f) for f in os.listdir('out'))//1024, 'KB')
