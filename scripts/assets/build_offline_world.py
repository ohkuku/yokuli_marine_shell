#!/usr/bin/env python3
"""将 Natural Earth 原始 GeoJSON 裁减为随 APK 发布的离线世界地图资产。

只删除未使用的属性；保留陆地、湖泊、河流和边界原始几何，不把世界底图
伪装成港湾级航海图。主干道路保留源 scalerank <= 6，排除渡轮和小径。
运行时不需要 Python、网络、字体服务或外部地图账号。

用法：python3 scripts/assets/build_offline_world.py --source-dir /path/to/geojson
源文件名与 Natural Earth 官方仓库 geojson 目录一致。
"""

import argparse
import collections
import hashlib
import json
import math
from pathlib import Path


SOURCES = {
    "land": "ne_10m_land",
    "lakes": "ne_10m_lakes",
    "rivers": "ne_10m_rivers_lake_centerlines",
    "boundaries": "ne_10m_admin_0_boundary_lines_land",
    "countries": "ne_10m_admin_0_countries",
    "cities": "ne_10m_populated_places",
    "seas": "ne_10m_geography_marine_polys",
    "roads": "ne_10m_roads",
}
BASE_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"


def properties(feature):
    return {key.lower(): value for key, value in feature["properties"].items()}


def number(value, fallback=0):
    return value if isinstance(value, (float, int)) and math.isfinite(value) and value >= 0 else fallback


def text(value):
    return value.strip() if isinstance(value, str) else ""


def names(props):
    # 缺少中文译名时保留空字符串，客户端回退到真实的英文/本地名称。
    return {
        "name_en": text(props.get("name_en")) or text(props.get("name")),
        "name_zh": text(props.get("name_zh")),
    }


def rank(props):
    return number(props.get("scalerank"), number(props.get("labelrank"), 9))


def minimum_zoom(props, label=False):
    preferred = props.get("min_label") if label else props.get("min_zoom")
    return min(12, number(preferred, number(props.get("min_zoom"), rank(props))))


def ring_area(ring):
    return sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(ring, ring[1:])) / 2


def point_in_ring(point, ring):
    x, y = point
    inside = False
    for a, b in zip(ring, ring[1:]):
        if (a[1] > y) != (b[1] > y):
            crossing = a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1])
            if crossing > x:
                inside = not inside
    return inside


def interior_point(geometry):
    """从源面内部计算标注点；不增加/猜测地理要素或地名。

    国家优先使用源 LABEL_X/Y。海域和湖泊没有点坐标时，取最大面质心，
    若质心落在凹面之外或洞内，则取中间扫描线的最长内部线段中点。
    """
    polygons = [geometry["coordinates"]] if geometry["type"] == "Polygon" else geometry["coordinates"]
    polygon = max(polygons, key=lambda part: abs(ring_area(part[0])))
    outer = polygon[0]
    area = ring_area(outer)
    if area:
        cx = sum((a[0] + b[0]) * (a[0] * b[1] - b[0] * a[1]) for a, b in zip(outer, outer[1:])) / (6 * area)
        cy = sum((a[1] + b[1]) * (a[0] * b[1] - b[0] * a[1]) for a, b in zip(outer, outer[1:])) / (6 * area)
        if point_in_ring((cx, cy), outer) and not any(point_in_ring((cx, cy), hole) for hole in polygon[1:]):
            return [round(cx, 6), round(cy, 6)]
    ys = sorted(set(p[1] for p in outer))
    mid = (ys[0] + ys[-1]) / 2
    candidates = sorted(((a + b) / 2 for a, b in zip(ys, ys[1:]) if a != b), key=lambda y: abs(y - mid))
    for y in candidates:
        intersections = []
        for ring in polygon:
            for a, b in zip(ring, ring[1:]):
                if (a[1] > y) != (b[1] > y):
                    intersections.append(a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]))
        intersections.sort()
        spans = list(zip(intersections[::2], intersections[1::2]))
        if spans:
            left, right = max(spans, key=lambda span: span[1] - span[0])
            return [round((left + right) / 2, 6), round(y, 6)]
    raise ValueError("Natural Earth label polygon has no interior")


def feature(kind, source, index, props, geometry):
    original = properties(source)
    return {
        "type": "Feature",
        "id": kind + "-" + str(original.get("ne_id") or original.get("uident") or index),
        "properties": props,
        "geometry": geometry,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parents[2] / "app-shell/src/main/assets/maps")
    parser.add_argument("--retrieved-on", default="2026-09-25")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    data, provenance, exports = {}, [], []
    for kind, name in SOURCES.items():
        raw = (args.source_dir / (name + ".geojson")).read_bytes()
        data[kind] = json.loads(raw)["features"]
        provenance.append({"name": name, "url": BASE_URL + name + ".geojson", "sha256": hashlib.sha256(raw).hexdigest(), "source_features": len(data[kind])})

    def write(name, features):
        result = json.dumps({"type": "FeatureCollection", "features": features}, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
        (args.output_dir / name).write_bytes(result)
        exports.append({"file": name, "features": len(features), "bytes": len(result), "sha256": hashlib.sha256(result).hexdigest()})

    for kind in ("land", "lakes", "rivers", "boundaries", "roads"):
        compact = []
        for index, original in enumerate(data[kind]):
            p = properties(original)
            if kind == "roads" and (rank(p) > 6 or "ferry" in text(p.get("type")).lower() or p.get("type") == "Track"):
                continue
            props = {"rank": rank(p), "min_zoom": minimum_zoom(p)}
            if kind in ("lakes", "rivers"):
                props.update(names(p))
            elif kind == "roads":
                props.update({"road_type": text(p.get("type")), "name_en": text(p.get("name"))})
            elif kind == "boundaries":
                classification = text(p.get("featurecla"))
                props.update({"feature_class": classification, "disputed": any(word in classification.lower() for word in ("disput", "indefinite", "claim", "unrecognized"))})
            compact.append(feature(kind, original, index, props, original["geometry"]))
        write("world_" + kind + ".geojson", compact)

    labels = []
    for source_kind, kind in (("countries", "country"), ("cities", "city"), ("seas", "sea"), ("lakes", "lake")):
        for index, original in enumerate(data[source_kind]):
            p = properties(original)
            props = {**names(p), "kind": kind, "rank": rank(p), "min_zoom": minimum_zoom(p, label=kind != "city")}
            if not props["name_en"] and not props["name_zh"]:
                continue
            if kind == "country":
                props["rank"] = number(p.get("labelrank"), rank(p))
                coords = [p["label_x"], p["label_y"]]
            elif kind == "city":
                coords = original["geometry"]["coordinates"]
            else:
                coords = interior_point(original["geometry"])
            if kind in ("country", "sea"):
                props["max_zoom"] = number(p.get("max_label"), 12)
            labels.append(feature(kind, original, index, props, {"type": "Point", "coordinates": coords}))
    write("world_labels.geojson", labels)

    manifest = {
        "title": "Yokuli built-in offline world background",
        "dataset": "Natural Earth",
        "scale": "1:10,000,000 (10m means ten million map scale, not 10-metre accuracy)",
        "retrieved_on": args.retrieved_on,
        "license": "Public domain",
        "license_url": "https://www.naturalearthdata.com/about/terms-of-use/",
        "attribution": "Made with Natural Earth.",
        "scope_zh": "世界背景地图：海岸、湖泊、主要河流、国家边界、主干道路和地名。不是港湾级海图，不含测深、潮汐、航标或其他适航保证。",
        "transformations_zh": ["保留物理面/线的源几何，裁减非渲染属性", "道路仅保留 scalerank <= 6，排除渡轮和小径", "国家使用源标签点；海域和湖泊从源面计算内部标签点", "保留源中英文名称；缺失译名为空，运行时回退到真实英文名称"],
        "sources": provenance,
        "exports": exports,
        "label_kinds": dict(collections.Counter(f["properties"]["kind"] for f in labels)),
    }
    (args.output_dir / "world_sources.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for exported in exports:
        print(exported["file"], exported["features"], exported["bytes"])
    print("Label kinds:", manifest["label_kinds"])


if __name__ == "__main__":
    main()
