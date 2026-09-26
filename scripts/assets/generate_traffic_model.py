#!/usr/bin/env python3
"""Original CC0 AIS display vessel, without textures or downloaded geometry.

X starboard, Y up, -Z bow. Bounds stay inside the production pick volume:
X ±0.5, Z ±0.5, Y -0.1..0.32. The silhouette is illustrative, not vessel type
or height evidence. Primitive order is shared with AisTrafficRenderer3D.paint.
"""
import json
import math
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / 'app-shell/src/main/assets/ais/traffic-vessel.glb'
PARTS = [[] for _ in range(4)]


def triangle(part, a, b, c):
    u = tuple(b[i] - a[i] for i in range(3))
    v = tuple(c[i] - a[i] for i in range(3))
    n = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
    length = math.sqrt(sum(x*x for x in n))
    if length < 1e-9:
        return
    normal = tuple(x/length for x in n)
    PARTS[part].extend((p, normal) for p in (a, b, c))


def quad(part, a, b, c, d):
    triangle(part, a, b, c)
    triangle(part, a, c, d)


def width(z):
    # Fine bow and full shoulders, then a flat transom. C1 smooth at each station.
    stations = [(-.5, .004), (-.42, .21), (-.25, .43), (-.05, .5), (.25, .47), (.5, .36)]
    for (a, wa), (b, wb) in zip(stations, stations[1:]):
        if a <= z <= b:
            t = (z-a)/(b-a)
            t = t*t*(3-2*t)
            return wa+(wb-wa)*t
    return stations[-1][1]


def sheer(z):
    return .08 + .045 * max(0, -z*2) ** 2


def section(z, angle):
    bottom = -.1 + .08 * max(0, -z*2) ** 3
    return (width(z)*math.sin(angle), bottom+(sheer(z)-bottom)*(1-math.cos(angle)), z)


for i in range(40):
    z0, z1 = -.5+i/40, -.5+(i+1)/40
    for j in range(16):
        a0, a1 = -math.pi/2+j*math.pi/16, -math.pi/2+(j+1)*math.pi/16
        quad(0, section(z0, a0), section(z1, a0), section(z1, a1), section(z0, a1))
    quad(1, (-width(z0), sheer(z0), z0), (-width(z1), sheer(z1), z1),
         (width(z1), sheer(z1), z1), (width(z0), sheer(z0), z0))
quad(0, (-width(.5), sheer(.5), .5), (width(.5), sheer(.5), .5),
     (width(.5)*.76, -.08, .5), (-width(.5)*.76, -.08, .5))

# A low swept wheelhouse: distinguish glass from hull even at compact size.
x0, x1, front, back, floor, roof = .25, .20, -.035, .25, .082, .248
quad(1, (-x0,floor,front), (x0,floor,front), (x1,roof,front+.045), (-x1,roof,front+.045))
quad(1, (x0,floor,front), (x0,floor,back), (x1,roof,back-.018), (x1,roof,front+.045))
quad(1, (-x0,floor,back), (-x0,floor,front), (-x1,roof,front+.045), (-x1,roof,back-.018))
quad(1, (x0,floor,back), (-x0,floor,back), (-x1,roof,back-.018), (x1,roof,back-.018))
quad(1, (-x1,roof,front+.045), (x1,roof,front+.045), (x1,roof,back-.018), (-x1,roof,back-.018))
for side in (-1, 1):
    x = side*.214
    quad(2, (x,.177,.048), (x,.177,.197), (side*.202,.235,.207), (side*.202,.235,.041))
quad(2, (-.204,.176,.001), (.204,.176,.001), (.185,.231,.017), (-.185,.231,.017))
quad(2, (.204,.176,.240), (-.204,.176,.240), (-.185,.228,.237), (.185,.228,.237))

# Small mast / transverse sensor bar. Within fixed production bounds.
def box(part, x, y, z, dx, dy, dz):
    p=[(x+sx*dx/2,y+sy*dy/2,z+sz*dz/2) for sx,sy,sz in
       [(-1,-1,-1),(1,-1,-1),(1,-1,1),(-1,-1,1),(-1,1,-1),(1,1,-1),(1,1,1),(-1,1,1)]]
    for face in [(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)]:
        quad(part, *(p[i] for i in face))
box(3, 0,.275,.16,.025,.054,.025)
box(3, 0,.303,.16,.21,.014,.03)

blob = bytearray()
views, accessors, primitives = [], [], []
for part, vertices in enumerate(PARTS):
    attrs = {}
    for index, name in ((0,'POSITION'), (1,'NORMAL')):
        offset = len(blob)
        values = [entry[index] for entry in vertices]
        for vector in values:
            blob.extend(struct.pack('<fff', *vector))
        views.append({'buffer':0,'byteOffset':offset,'byteLength':len(blob)-offset,'target':34962})
        accessor={'bufferView':len(views)-1,'componentType':5126,'count':len(values),'type':'VEC3'}
        if name == 'POSITION':
            accessor['min']=[min(v[i] for v in values) for i in range(3)]
            accessor['max']=[max(v[i] for v in values) for i in range(3)]
        accessors.append(accessor)
        attrs[name]=len(accessors)-1
    primitives.append({'attributes':attrs,'material':part})

materials=[]
for name, shade, roughness in [('hull',.72,.52),('deck',.80,.78),('glass',.06,.27),('fittings',.44,.48)]:
    materials.append({'name':name,'doubleSided':True,'pbrMetallicRoughness':{
        'baseColorFactor':[shade,shade,shade,1],'metallicFactor':.04,'roughnessFactor':roughness}})
gltf={'asset':{'version':'2.0','generator':'Yokuli original display vessel; CC0'},'scene':0,
      'scenes':[{'nodes':[0]}],'nodes':[{'name':'traffic-vessel','mesh':0}],
      'meshes':[{'primitives':primitives}],'materials':materials,
      'buffers':[{'byteLength':len(blob)}],'bufferViews':views,'accessors':accessors}
metadata=json.dumps(gltf,separators=(',',':')).encode()
metadata+=b' '*((-len(metadata))%4)
blob+=b'\0'*((-len(blob))%4)
output=struct.pack('<III',0x46546c67,2,28+len(metadata)+len(blob))
output+=struct.pack('<II',len(metadata),0x4e4f534a)+metadata
output+=struct.pack('<II',len(blob),0x004e4942)+blob
DESTINATION.write_bytes(output)
print(f'{DESTINATION.relative_to(ROOT)}: {len(output)} bytes')
