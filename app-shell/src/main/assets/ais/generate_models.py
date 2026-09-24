#!/usr/bin/env python3
"""Yokuli original CC0 traffic symbols; regenerate packaged meshes with Python 3.

Unit hull length and beam are scaled by the renderer from trusted AIS dimensions.
Cabin/height are always illustrative. No downloaded models, maps or textures.
"""
import json
import math
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent


def write_model(name, faces, color):
    vertices = []
    normals = []
    for face in faces:
        # 输出右手坐标 X 右舷、Y 上、Z 船艉；同时将建模轮廓的内向绕序变为外向。
        # 渲染器可以使用正常旋转与正比例缩放，不需要负行列式的镜像变换。
        if name != 'traffic-plane':
            face = [(x, y, -z) for x, y, z in face]
        for index in range(1, len(face) - 1):
            a, b, c = face[0], face[index], face[index + 1]
            u = [b[i] - a[i] for i in range(3)]
            v = [c[i] - a[i] for i in range(3)]
            n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
            length = math.sqrt(sum(value * value for value in n)) or 1
            n = [value / length for value in n]
            vertices.extend((a, b, c))
            normals.extend((n, n, n))
    positions = b''.join(struct.pack('<fff', *point) for point in vertices)
    normal_bytes = b''.join(struct.pack('<fff', *point) for point in normals)
    data = positions + normal_bytes
    gltf = {
        'asset': {'version':'2.0', 'generator':'Yokuli original procedural traffic geometry; CC0'},
        'scene':0, 'scenes':[{'nodes':[0]}], 'nodes':[{'name':name, 'mesh':0}],
        'meshes':[{'primitives':[{'attributes':{'POSITION':0,'NORMAL':1},'material':0}]}],
        'materials':[{'name':'traffic', 'doubleSided':True,'pbrMetallicRoughness':{
            'baseColorFactor':color,'metallicFactor':0.08,'roughnessFactor':0.72}}],
        'buffers':[{'byteLength':len(data)}],
        'bufferViews':[{'buffer':0,'byteOffset':0,'byteLength':len(positions),'target':34962},
                       {'buffer':0,'byteOffset':len(positions),'byteLength':len(normal_bytes),'target':34962}],
        'accessors':[{'bufferView':0,'componentType':5126,'count':len(vertices),'type':'VEC3',
                      'min':[min(v[i] for v in vertices) for i in range(3)],
                      'max':[max(v[i] for v in vertices) for i in range(3)]},
                     {'bufferView':1,'componentType':5126,'count':len(normals),'type':'VEC3'}],
    }
    encoded = json.dumps(gltf,separators=(',',':')).encode()
    encoded += b' ' * (-len(encoded) % 4)
    data += b'\0' * (-len(data) % 4)
    total = 12 + 8 + len(encoded) + 8 + len(data)
    (ROOT / (name + '.glb')).write_bytes(struct.pack('<III',0x46546c67,2,total)
        + struct.pack('<II',len(encoded),0x4e4f534a) + encoded
        + struct.pack('<II',len(data),0x004e4942) + data)


def box(x0, x1, y0, y1, z0, z1):
    a,b,c,d=(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)
    e,f,g,h=(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)
    return [(a,d,c,b),(e,f,g,h),(a,b,f,e),(d,h,g,c),(a,e,h,d),(b,c,g,f)]


outline=[(-.38,-.5),(.38,-.5),(.5,-.3),(.45,.23),(0,.5),(-.45,.23),(-.5,-.3)]
lower=[(x*.73,-.10,z*.90) for x,z in outline]
upper=[(x,.10,z) for x,z in outline]
ship=[tuple(reversed(lower)),tuple(upper)]
for i in range(len(outline)):
    j=(i+1)%len(outline)
    ship.append((lower[i],lower[j],upper[j],upper[i]))
ship += box(-.28,.28,.10,.25,-.38,-.05)
ship += box(-.22,.22,.25,.32,-.29,-.12)
write_model('traffic-vessel',ship,[.67,.83,.92,1])
equator=[(.5,.22,0),(0,.22,.5),(-.5,.22,0),(0,.22,-.5)]
neutral=[]
for i in range(4):
    a,b=equator[i],equator[(i+1)%4]
    neutral.extend([((0,.65,0),a,b),((0,0,0),b,a)])
write_model('traffic-neutral',neutral,[.67,.83,.92,1])
write_model('traffic-plane',[[(-1,-.02,-1),(-1,-.02,1),(1,-.02,1),(1,-.02,-1)]],[.025,.065,.085,1])
