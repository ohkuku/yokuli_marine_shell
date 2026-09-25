#!/usr/bin/env python3
"""从品牌SVG母版生成实际Android资源。只生成资产，不修改构建流程。"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "design/brand"
OUT = ROOT / "core/design/src/main/res/drawable"
ANDROID = "http://schemas.android.com/apk/res/android"
SVG = "{http://www.w3.org/2000/svg}"
ET.register_namespace("android", ANDROID)
def a(name): return "{" + ANDROID + "}" + name

def paths(name):
    return [{"name": p.attrib["id"], "fillColor": p.attrib["fill"],
             "fillType": "evenOdd" if p.attrib.get("fill-rule") == "evenodd" else "nonZero",
             "pathData": " ".join(p.attrib["d"].split())}
            for p in ET.parse(SOURCE / name).getroot().iter(SVG + "path")]

mark = paths("yokuli-mark.svg")
wordmark = paths("yokuli-wordmark.svg")

def vector(name, width, height, viewport, groups, background=None):
    root = ET.Element("vector", {a("width"): f"{width}dp", a("height"): f"{height}dp",
        a("viewportWidth"): str(viewport[0]), a("viewportHeight"): str(viewport[1])})
    if background:
        ET.SubElement(root, "path", {a("fillColor"): background,
            a("pathData"): f"M0,0H{viewport[0]}V{viewport[1]}H0Z"})
    for geometry, scale, dx, dy, monochrome in groups:
        group = ET.SubElement(root, "group", {a("scaleX"):str(scale), a("scaleY"):str(scale),
            a("translateX"):str(dx), a("translateY"):str(dy)})
        for item in geometry:
            values = dict(item)
            if monochrome: values["fillColor"] = "#FFFFFF"
            ET.SubElement(group, "path", {a(k):v for k,v in values.items()})
    ET.indent(root, space="    ")
    text = ET.tostring(root, encoding="unicode")
    (OUT / f"{name}.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from design/brand SVG masters; run scripts/generate_brand_assets.py. -->\n' + text + '\n')

OUT.mkdir(parents=True, exist_ok=True)
vector("yokuli_mark_body",64,64,(64,64),[([mark[0]],1,0,0,False)])
vector("yokuli_mark_beacon",64,64,(64,64),[([mark[1]],1,0,0,True)])
vector("yokuli_wordmark",296,60,(296,60),[(wordmark,1,0,0,False)])
# 标识全部轮廓落在108画布的中央直径66圆形安全区域，圆形/方形遮罩使用同一几何。
vector("yokuli_launcher_foreground",108,108,(108,108),[(mark,.93,23.31,22.38,False)])
vector("yokuli_launcher_monochrome",108,108,(108,108),[(mark,.93,23.31,22.38,True)])
vector("yokuli_legacy_icon",108,108,(108,108),[(mark,.93,23.31,22.38,False)],"#081E29")
# 无背景启动图：288dp画布内全部轮廓落在中心直径192dp圆形安全区。
vector("yokuli_splash_symbol",288,288,(96,96),[(mark,.90,18.3,17.4,False)])
vector("yokuli_splash_wordmark",200,80,(200,80),[(wordmark,.54,20,22,False)])
# 可直接使用的矢量发布资产。小字是母版轮廓，不要求访问者安装任何字体。
body = (SOURCE / "yokuli-mark.svg").read_text().split('<path',1)[1].split('</svg>')[0]
mark_content = '<path' + body
word_content = '<path' + (SOURCE / "yokuli-wordmark.svg").read_text().split('<path',1)[1].split('</svg>')[0]
(SOURCE / "yokuli-os.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="256" viewBox="0 0 320 256">\n<title>Yokuli OS</title>\n<rect width="320" height="256" rx="0" fill="#081E29"/>\n<g transform="translate(96 30) scale(2)">'+mark_content+'</g>\n<g transform="translate(54 191) scale(.72)">'+word_content+'</g>\n</svg>\n')
print("Generated 8 production vectors and brand lockup")
