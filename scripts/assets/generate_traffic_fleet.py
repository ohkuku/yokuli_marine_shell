#!/usr/bin/env python3
"""CC0 maritime display silhouettes. Class evidence comes from AIS; geometry is symbolic.

Builds the production traffic-vessel.glb, with one named mesh per reported ship class.
X starboard, Y up, -Z bow; normalized length and beam are one. No downloaded assets.
"""
import json
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / 'app-shell/src/main/assets/ais/traffic-vessel.glb'
FORMS = ('GENERIC', 'SAILING', 'MOTOR', 'FISHING', 'TUG', 'PASSENGER', 'CARGO', 'TANKER')
parts = None


def triangle(part, a, b, c):
    u, v = [b[i]-a[i] for i in range(3)], [c[i]-a[i] for i in range(3)]
    normal = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
    length = math.sqrt(sum(x*x for x in normal))
    if length < 1e-10:
        return
    n = tuple(x/length for x in normal)
    parts[part].extend((p, n) for p in (a,b,c))


def quad(part, a,b,c,d):
    triangle(part,a,b,c); triangle(part,a,c,d)


def box(part, x,y,z,w,h,d):
    p=[(x+sx*w/2,y+sy*h,z+sz*d/2) for sx,sy,sz in
       [(-1,0,-1),(1,0,-1),(1,0,1),(-1,0,1),(-1,1,-1),(1,1,-1),(1,1,1),(-1,1,1)]]
    for face in [(4,7,6,5),(0,1,2,3),(0,4,5,1),(3,2,6,7),(0,3,7,4),(1,5,6,2)]:
        quad(part,*(p[i] for i in face))


def hull(form):
    commercial = form in ('CARGO','TANKER','PASSENGER')
    deck = .075 if commercial else .045
    for i in range(24):
        z0,z1=-.5+i/24,-.5+(i+1)/24
        def section(z, a):
            # Narrow bow, rounded shoulders and a full stern. Height is class illustration only.
            width=.5*min(1., max(.008,(z+.5)/(.17 if commercial else .31)))
            width*=1.-.10*max(0.,(z-.18)/.32)
            y=-.07+(deck+.07)*(1-math.cos(a))
            return (width*math.sin(a),y,z)
        for j in range(10):
            a0,a1=-math.pi/2+j*math.pi/10,-math.pi/2+(j+1)*math.pi/10
            quad(0,section(z0,a0),section(z0,a1),section(z1,a1),section(z1,a0))
        quad(1,section(z0,-math.pi/2),section(z1,-math.pi/2),section(z1,math.pi/2),section(z0,math.pi/2))
    box(0,0,-.07,.495,.90,deck+.07,.01)
    return deck


def cabin(x,y,z,w,h,d):
    box(1,x,y,z,w,h,d)
    # Dark wrapped glazing and a small roof keep the silhouette readable in both themes.
    box(2,x,y+h*.40,z,w*1.01,h*.35,d*1.01)
    box(1,x,y+h*.91,z,w*1.08,h*.09,d*1.08)


def model(form):
    global parts
    parts=[[] for _ in range(4)]
    deck=hull(form)
    if form=='SAILING':
        cabin(0,deck,.10,.42,.07,.36)
        box(3,0,deck,-.04,.012,.86,.012)
        box(3,0,.15,.13,.014,.013,.34)
        # Class silhouette only; it does not describe current sail deployment.
        triangle(1,(-.006,.91,-.04),(-.006,.15,-.04),(-.006,.15,.30))
        triangle(1,(.006,.91,-.04),(.006,.15,.30),(.006,.15,-.04))
    elif form=='MOTOR':
        cabin(0,deck,.05,.55,.12,.45)
        box(3,0,.17,.20,.025,.10,.02)
    elif form=='FISHING':
        cabin(0,deck,-.20,.62,.17,.22)
        box(3,-.26,deck,.29,.02,.29,.02); box(3,.26,deck,.29,.02,.29,.02)
        box(3,0,deck+.27,.29,.55,.025,.025)
    elif form=='TUG':
        cabin(0,deck,-.09,.70,.18,.32)
        box(0,0,deck+.01,.28,.79,.07,.23)
        box(3,0,deck+.18,-.03,.04,.13,.04)
    elif form=='PASSENGER':
        cabin(0,deck,.04,.74,.085,.76)
        cabin(0,deck+.085,.08,.64,.075,.59)
        cabin(0,deck+.16,-.11,.48,.055,.18)
    elif form=='CARGO':
        cabin(0,deck,.32,.76,.18,.19)
        # One restrained cargo volume; individual containers are not observable AIS facts.
        box(1,0,deck,-.10,.76,.11,.60)
        box(3,0,deck+.18,.37,.14,.045,.05)
    elif form=='TANKER':
        cabin(0,deck,.34,.77,.17,.18)
        box(3,0,deck+.007,-.09,.07,.026,.62)
        for z in (-.29,-.05,.19):
            box(3,0,deck+.027,z,.44,.018,.028)
    else:
        cabin(0,deck,.11,.52,.13,.38)
        box(3,0,deck+.13,.17,.025,.09,.025)
    return parts


blob=bytearray(); views=[]; accessors=[]; meshes=[]
for form in FORMS:
    primitives=[]
    for part,vertices in enumerate(model(form)):
        if not vertices:
            continue
        attrs={}
        for index,name in ((0,'POSITION'),(1,'NORMAL')):
            offset=len(blob)
            values=[entry[index] for entry in vertices]
            for value in values: blob.extend(struct.pack('<fff',*value))
            views.append({'buffer':0,'byteOffset':offset,'byteLength':len(blob)-offset,'target':34962})
            a={'bufferView':len(views)-1,'componentType':5126,'count':len(values),'type':'VEC3'}
            if index==0:
                a['min']=[min(v[i] for v in values) for i in range(3)]
                a['max']=[max(v[i] for v in values) for i in range(3)]
            accessors.append(a); attrs[name]=len(accessors)-1
        primitives.append({'attributes':attrs,'material':part})
    meshes.append({'name':form,'primitives':primitives})
materials=[]
for name,shade,rough in [('hull',.62,.70),('deck',.86,.85),('glass',.07,.40),('fittings',.35,.68)]:
    materials.append({'name':name,'doubleSided':True,'pbrMetallicRoughness':{
        'baseColorFactor':[shade,shade,shade,1],'metallicFactor':0,'roughnessFactor':rough}})
nodes=[{'name':'traffic-fleet','children':list(range(1,len(FORMS)+1))}]
nodes.extend({'name':'display:'+form,'mesh':i} for i,form in enumerate(FORMS))
doc={'asset':{'version':'2.0','generator':'Yokuli original AIS class silhouettes; CC0'},'scene':0,
     'scenes':[{'nodes':[0]}],'nodes':nodes,'meshes':meshes,'materials':materials,
     'buffers':[{'byteLength':len(blob)}],'bufferViews':views,'accessors':accessors}
meta=json.dumps(doc,separators=(',',':')).encode();meta+=b' '*((-len(meta))%4)
blob+=b'\0'*((-len(blob))%4)
out=struct.pack('<III',0x46546c67,2,28+len(meta)+len(blob))
out+=struct.pack('<II',len(meta),0x4e4f534a)+meta+struct.pack('<II',len(blob),0x004e4942)+blob
DESTINATION.write_bytes(out)
print(f'{DESTINATION.relative_to(ROOT)}: {len(out)} bytes; {len(FORMS)} reported-class silhouettes')
