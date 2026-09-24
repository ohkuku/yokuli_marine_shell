#!/usr/bin/env python3
"""Build Yokuli's original, texture-free sloop as a self-contained glTF 2 GLB.

Only the Python standard library is required. Run from any directory:
    python3 scripts/assets/generate_vessel_model.py

CC0-1.0. The geometry is original; it contains no downloaded model or texture.
Coordinate contract: X starboard, Y up, Z bow, waterline Y=0; metres are
illustrative model units, not the dimensions or sensor offsets of a real boat.
"""

from __future__ import annotations

import json
import math
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / "app-shell/src/main/assets/vessel/yokuli-sloop.glb"
PI = math.pi


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def scale(a, factor):
    return tuple(x * factor for x in a)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def normalized(v):
    length = math.sqrt(sum(x * x for x in v))
    return tuple(x / length for x in v) if length > 1e-12 else (0.0, 1.0, 0.0)


def interpolate(a, b, t):
    return tuple(x * (1 - t) + y * t for x, y in zip(a, b))


def profile(stations, z):
    """Monotone cubic Hermite loft avoids angular chines and curve overshoot."""
    if z <= stations[0][0]:
        return stations[0][1]
    if z >= stations[-1][0]:
        return stations[-1][1]
    slopes = [(b[1] - a[1]) / (b[0] - a[0])
              for a, b in zip(stations, stations[1:])]
    tangents = [slopes[0]]
    for previous, following in zip(slopes, slopes[1:]):
        tangents.append(0 if previous * following <= 0 else
                        2 * previous * following / (previous + following))
    tangents.append(slopes[-1])
    for i, (a, b) in enumerate(zip(stations, stations[1:])):
        if a[0] <= z <= b[0]:
            width = b[0] - a[0]
            t = (z - a[0]) / width
            return ((2 * t ** 3 - 3 * t ** 2 + 1) * a[1]
                    + (t ** 3 - 2 * t ** 2 + t) * width * tangents[i]
                    + (-2 * t ** 3 + 3 * t ** 2) * b[1]
                    + (t ** 3 - t ** 2) * width * tangents[i + 1])
    raise ValueError("Station outside loft")


WIDTH = [(-1.90, .445), (-1.55, .545), (-.8, .63), (0, .615),
         (.65, .49), (1.3, .30), (1.8, .11), (2.0, .005)]
SHEER = [(-1.90, .31), (-.8, .315), (0, .345), (1, .42), (2, .55)]
BOTTOM = [(-1.90, -.13), (-1.45, -.33), (-.7, -.50),
          (.2, -.51), (1, -.31), (1.65, .02), (2, .38)]


def beam(z):
    return profile(WIDTH, z)


def sheer(z):
    return profile(SHEER, z)


def deck_y(x, z):
    return sheer(z) + .024 * (1 - (x / max(.005, beam(z))) ** 2)


def hull_point(z, theta):
    bottom = profile(BOTTOM, z)
    return (beam(z) * math.sin(theta),
            bottom + (sheer(z) - bottom) * (1 - math.cos(theta)), z)


class Model:
    def __init__(self):
        self.materials = []
        self.groups = {}
        self.anchors = []

    def material(self, name, color, metallic=0.0, roughness=.65,
                 double_sided=False):
        index = len(self.materials)
        self.materials.append({
            "name": name,
            "pbrMetallicRoughness": {"baseColorFactor": [*color, 1.0],
                                    "metallicFactor": metallic,
                                    "roughnessFactor": roughness},
            "doubleSided": double_sided,
        })
        return index

    def mesh(self, group, material, points, triangles):
        """Accumulate smooth area-weighted normals before joining components."""
        vertices, normals, indices = self.groups.setdefault(group, {}).setdefault(
            material, ([], [], []))
        offset = len(vertices)
        vertex_normals = [(0.0, 0.0, 0.0) for _ in points]
        valid_triangles = []
        for a, b, c in triangles:
            normal = cross(sub(points[b], points[a]), sub(points[c], points[a]))
            if sum(n * n for n in normal) < 1e-20:
                continue
            valid_triangles.append((a, b, c))
            for vertex in (a, b, c):
                vertex_normals[vertex] = add(vertex_normals[vertex], normal)
        vertices.extend(points)
        normals.extend(normalized(n) for n in vertex_normals)
        indices.extend(v + offset for tri in valid_triangles for v in tri)

    def grid(self, group, material, rows, reverse=False):
        count = len(rows[0])
        triangles = []
        for i in range(len(rows) - 1):
            for j in range(count - 1):
                a, b = i * count + j, (i + 1) * count + j
                triangles.extend([(a, a + 1, b + 1), (a, b + 1, b)])
        if reverse:
            triangles = [(c, b, a) for a, b, c in triangles]
        self.mesh(group, material, [p for row in rows for p in row], triangles)

    def rod(self, group, material, start, end, radius, end_radius=None, sides=8):
        direction = normalized(sub(end, start))
        reference = (0, 1, 0) if abs(direction[1]) < .9 else (1, 0, 0)
        right = normalized(cross(direction, reference))
        up = normalized(cross(direction, right))
        end_radius = radius if end_radius is None else end_radius
        rows = []
        for center, r in ((start, radius), (end, end_radius)):
            rows.append([add(center, add(scale(right, r * math.cos(i * 2 * PI / sides)),
                                         scale(up, r * math.sin(i * 2 * PI / sides))))
                         for i in range(sides + 1)])
        self.grid(group, material, rows)
        # Flat caps have independent vertices so cylinder walls remain smooth.
        self.mesh(group, material, [start, *rows[0][:-1]],
                  [(0, 1 + (i + 1) % sides, 1 + i) for i in range(sides)])
        self.mesh(group, material, [end, *rows[1][:-1]],
                  [(0, 1 + i, 1 + (i + 1) % sides) for i in range(sides)])

    def tube(self, group, material, points, radius, sides=7):
        for start, end in zip(points, points[1:]):
            self.rod(group, material, start, end, radius, sides=sides)

    def ellipsoid(self, group, material, center, radii, rings=10, sides=20):
        rows = []
        for latitude in range(rings + 1):
            theta = PI * latitude / rings
            rows.append([add(center, (radii[0] * math.sin(theta) * math.cos(2 * PI * i / sides),
                                      radii[1] * math.cos(theta),
                                      radii[2] * math.sin(theta) * math.sin(2 * PI * i / sides)))
                         for i in range(sides + 1)])
        self.grid(group, material, rows)

    def anchor(self, name, position):
        self.anchors.append({"name": name, "translation": list(position),
                             "extras": {"semanticAnchor": True}})

    def write(self, destination):
        buffer = bytearray()
        views, accessors, meshes, nodes = [], [], [], []

        def accessor(values, kind, components, component_type, target, extrema=False):
            while len(buffer) % 4:
                buffer.append(0)
            start = len(buffer)
            for value in values:
                buffer.extend(struct.pack("<" + kind * components,
                                          *(value if components > 1 else (value,))))
            view = len(views)
            views.append({"buffer": 0, "byteOffset": start,
                          "byteLength": len(buffer) - start, "target": target})
            result = {"bufferView": view, "componentType": component_type,
                      "count": len(values), "type": "SCALAR" if components == 1 else "VEC3"}
            if extrema:
                result["min"] = [min(p[i] for p in values) for i in range(components)]
                result["max"] = [max(p[i] for p in values) for i in range(components)]
            accessors.append(result)
            return len(accessors) - 1

        triangle_count = 0
        for name, materials in self.groups.items():
            primitives = []
            for material, (positions, normals, indices) in materials.items():
                triangle_count += len(indices) // 3
                primitives.append({
                    "attributes": {"POSITION": accessor(positions, "f", 3, 5126, 34962, True),
                                   "NORMAL": accessor(normals, "f", 3, 5126, 34962)},
                    "indices": accessor(indices, "H" if len(positions) < 65536 else "I",
                                        1, 5123 if len(positions) < 65536 else 5125, 34963),
                    "material": material,
                    "mode": 4,
                })
            meshes.append({"name": name, "primitives": primitives})
            nodes.append({"name": name, "mesh": len(meshes) - 1})
        nodes.extend(self.anchors)
        children = list(range(len(nodes)))
        nodes.append({"name": "yokuli-sloop", "children": children,
                      "extras": {"coordinateFrame": "X starboard, Y up, Z bow; waterline Y=0",
                                 "purpose": "Semantic vessel, not a measured real vessel",
                                 "version": "1.0.0", "license": "CC0-1.0"}})
        payload = {
            "asset": {"version": "2.0", "generator": "Yokuli procedural sloop 1.0.0",
                      "copyright": "Original Yokuli geometry, dedicated to the public domain under CC0-1.0"},
            "scene": 0, "scenes": [{"nodes": [len(nodes) - 1]}],
            "nodes": nodes, "meshes": meshes, "materials": self.materials,
            "accessors": accessors, "bufferViews": views,
            "buffers": [{"byteLength": len(buffer)}],
        }
        metadata = json.dumps(payload, separators=(",", ":"), ensure_ascii=True).encode()
        metadata += b" " * (-len(metadata) % 4)
        buffer.extend(b"\0" * (-len(buffer) % 4))
        total = 12 + 8 + len(metadata) + 8 + len(buffer)
        destination.parent.mkdir(parents=True, exist_ok=True)
        with destination.open("wb") as output:
            output.write(struct.pack("<4sII", b"glTF", 2, total))
            output.write(struct.pack("<I4s", len(metadata), b"JSON"))
            output.write(metadata)
            output.write(struct.pack("<I4s", len(buffer), b"BIN\0"))
            output.write(buffer)
        print(f"Generated {destination.relative_to(ROOT)}: {triangle_count:,} triangles, {total:,} bytes")


def build():
    model = Model()
    navy = model.material("Deep ocean enamel", (.026, .105, .18), .12, .28)
    ivory = model.material("Warm porcelain deck", (.91, .91, .84), .0, .55)
    cloth = model.material("Ivory woven sails", (.96, .94, .85), .0, .88, True)
    seam = model.material("Sail panel seams", (.65, .67, .65), .0, .95)
    metal = model.material("Satin aluminum", (.58, .64, .69), .65, .36)
    rigging = model.material("Graphite rigging", (.18, .23, .26), .45, .46)
    glass = model.material("Blue smoked glazing", (.045, .115, .155), .28, .18)
    teak = model.material("Oiled teak seating", (.40, .22, .105), .0, .80)
    stripe = model.material("Warm silver cove stripe", (.81, .84, .79), .25, .40)
    fin = model.material("Keel and rudder", (.15, .23, .29), .25, .45)
    red = model.material("Port navigation housing", (.66, .065, .065), .15, .4)
    green = model.material("Starboard navigation housing", (.03, .48, .23), .15, .4)

    stations = sorted(set([-1.9 + 3.9 * i / 64 for i in range(65)] + [-1.52, -.55]))
    theta = [-PI / 2 + PI * j / 32 for j in range(33)]
    model.grid("hull", navy, [[hull_point(z, angle) for angle in theta] for z in stations])
    # The transom is closed separately, with a crisp edge instead of smoothing
    # the curved topsides into a rounded end cap.
    transom = [hull_point(-1.9, angle) for angle in theta]
    model.mesh("hull", navy, [(0, deck_y(0, -1.9), -1.9), *transom],
               [(0, i + 2, i + 1) for i in range(len(transom) - 1)])
    bow_cap = [hull_point(2, angle) for angle in theta]
    model.mesh("hull", navy, [(0, deck_y(0, 2), 2), *bow_cap],
               [(0, i + 1, i + 2) for i in range(len(bow_cap) - 1)])

    for side, name in ((-1, "port"), (1, "starboard")):
        # Thin inlaid cove stripe follows the loft, not a floating straight bar.
        rows = [[add(hull_point(z, side * angle), (side * .0015, 0, 0))
                 for angle in (1.404, 1.438)] for z in stations]
        model.grid(name, stripe, rows, reverse=side < 0)
        edge = [(side * (beam(z) + .003), sheer(z) + .005, z) for z in stations]
        model.tube(name, ivory, edge, .012, sides=6)

    # Crowned deck, with an actual open cockpit well aft of the cabin.
    for a, b in zip(stations, stations[1:]):
        cockpit = a >= -1.5200001 and b <= -.5499999
        sections = [(-1, -.26, False), (.26, 1, True)] if cockpit else [(-1, 1, None)]
        for left, right, fixed_side in sections:
            rows = []
            for z in (a, b):
                if fixed_side is False:
                    xs = [-beam(z), -.26]
                elif fixed_side is True:
                    xs = [.26, beam(z)]
                else:
                    xs = [-beam(z) + 2 * beam(z) * k / 12 for k in range(13)]
                rows.append([(x, deck_y(x, z), z) for x in xs])
            model.grid("deck", ivory, rows, reverse=True)
    model.grid("cockpit", ivory, [[(x, .205, z) for x in (-.26, .26)] for z in (-1.52, -.55)], True)
    for side in (-1, 1):
        model.grid("cockpit", ivory,
                   [[(side * .26, .205, z), (side * .26, deck_y(side * .26, z), z)]
                    for z in (-1.52, -.55)], reverse=side > 0)
        for n in range(3):
            x0 = side * (.285 + n * .038)
            x1 = x0 + side * .031
            model.grid("cockpit", teak,
                       [[(x, deck_y(x, z) + .008, z) for x in sorted((x0, x1))]
                        for z in (-1.46, -.62)], reverse=True)
    for z, reverse in ((-1.52, False), (-.55, True)):
        model.grid("cockpit", ivory,
                   [[(x, y, z) for x in (-.26, .26)]
                    for y in (.205, deck_y(.26, z))], reverse=reverse)

    cabin_width = [(-.55, .30), (-.4, .415), (.2, .405), (.66, .33), (1.06, .055)]
    cabin_height = [(-.55, .075), (-.36, .32), (.24, .335), (.67, .24), (1.06, .005)]

    def cabin_point(z, angle, offset=0):
        x = (profile(cabin_width, z) + offset) * math.sin(angle)
        y = deck_y(0, z) + profile(cabin_height, z) * max(0, math.cos(angle)) ** .43 + offset
        return (x, y, z)

    cabin_z = [-.55 + 1.61 * n / 32 for n in range(33)]
    cabin_theta = [-PI / 2 + PI * n / 24 for n in range(25)]
    model.grid("cabin", ivory, [[cabin_point(z, angle) for angle in cabin_theta] for z in cabin_z], True)
    for z, reverse in ((-.55, False), (1.06, True)):
        points = [(0, deck_y(0, z), z)] + [cabin_point(z, a) for a in cabin_theta]
        tris = [(0, n + 1, n + 2) for n in range(len(cabin_theta) - 1)]
        model.mesh("cabin", ivory, points, [(c, b, a) for a, b, c in tris] if reverse else tris)
    # Glazing sits on the curved cabin; three separate panes show scale without
    # turning the cabin into a generic box with a black stripe.
    for side in (-1, 1):
        for start, end in ((-.29, -.08), (-.02, .24), (.30, .58)):
            zs = [start + (end - start) * i / 6 for i in range(7)]
            rows = [[cabin_point(z, side * a, .004) for a in (1.03, 1.33)] for z in zs]
            model.grid("cabin-glazing", glass, rows, reverse=side > 0)
    model.grid("cabin-glazing", glass,
               [[cabin_point(z, a, .004) for a in (-.34, .34)] for z in (.09, .39)], True)

    def foil(group, levels, material):
        # Symmetric NACA section: finite thickness, rounded leading edge, swept
        # trailing edge. The foil narrows toward its tip instead of a flat slab.
        rows = []
        section = [(i / 18, -1) for i in range(19)] + [(i / 18, 1) for i in range(17, -1, -1)]
        for y, leading, trailing, ratio in levels:
            chord = leading - trailing
            row = []
            for u, side in section:
                thickness = 5 * ratio * chord * (.2969 * math.sqrt(u) - .1260 * u - .3516 * u ** 2
                                                + .2843 * u ** 3 - .1015 * u ** 4)
                row.append((side * thickness, y, leading - chord * u))
            row.append(row[0])
            rows.append(row)
        model.grid(group, material, rows)
        for row, reverse in ((rows[0], True), (rows[-1], False)):
            center = (0, row[0][1], (max(p[2] for p in row) + min(p[2] for p in row)) / 2)
            tris = [(0, i + 1, i + 2) for i in range(len(row) - 1)]
            model.mesh(group, material, [center, *row],
                       [(c, b, a) for a, b, c in tris] if reverse else tris)

    foil("keel", [(-.40, .42, -.55, .13), (-.62, .38, -.52, .12),
                  (-.96, .25, -.49, .115), (-1.12, .20, -.47, .12)], fin)
    model.ellipsoid("keel", fin, (0, -1.13, -.06), (.115, .07, .48))
    foil("rudder", [(-.10, -1.53, -1.88, .13), (-.48, -1.55, -1.84, .115),
                    (-.78, -1.62, -1.83, .1)], fin)
    model.rod("rudder", metal, (0, -.25, -1.61), (0, .28, -1.61), .023)

    mast_z = .19
    model.rod("mast", metal, (0, .62, mast_z), (0, 3.8, mast_z), .028, .013, sides=14)
    model.rod("mast", metal, (0, 1.02, mast_z), (.01, .995, -1.59), .024, .018, sides=10)
    model.rod("mast", metal, (-.35, 2.13, mast_z), (.35, 2.13, mast_z), .011, sides=8)
    model.rod("rigging", rigging, (0, 3.67, mast_z), (0, .575, 1.91), .004, sides=6)
    model.rod("rigging", rigging, (0, 3.67, mast_z), (0, .375, -1.84), .004, sides=6)
    for side in (-1, 1):
        model.tube("rigging", rigging, [(0, 3.5, mast_z), (side * .35, 2.13, mast_z),
                                       (side * .53, .38, mast_z)], .0045, sides=6)
        model.rod("rigging", rigging, (0, 2.13, mast_z), (side * .51, .38, -.1), .0035, sides=6)

    def sail(group, head, tack, clew, camber, subdivisions):
        def point(u, v):
            # Barycentric, softly cambered cloth. Shape is static: it represents
            # the generic vessel, never inferred wind or a fabricated live sail.
            a = 1 - u - v
            base = add(add(scale(head, a), scale(tack, u)), scale(clew, v))
            bulge = camber * 27 * a * u * v
            return add(base, (bulge, 0, 0))

        points, lookup = [], {}
        for i in range(subdivisions + 1):
            for j in range(subdivisions + 1 - i):
                lookup[i, j] = len(points)
                points.append(point(i / subdivisions, j / subdivisions))
        tris = []
        for i in range(subdivisions):
            for j in range(subdivisions - i):
                tris.append((lookup[i, j], lookup[i + 1, j], lookup[i, j + 1]))
                if i + j < subdivisions - 1:
                    tris.append((lookup[i + 1, j], lookup[i + 1, j + 1], lookup[i, j + 1]))
        model.mesh(group, cloth, points, tris)
        model.tube(group, seam, [head, tack, clew, head], .004, sides=6)
        for fraction in (.25, .49, .72):
            row = [point(fraction * (1 - q / 16), fraction * q / 16) for q in range(17)]
            model.tube(group, seam, row, .0017, sides=5)

    sail("mainsail", (.007, 3.62, .16), (.012, 1.075, .15), (.024, 1.06, -1.52), .15, 22)
    sail("headsail", (-.009, 3.44, .245), (-.01, .63, 1.80), (.025, 1.08, .45), -.12, 20)

    # Lifelines and pulpit follow the hull. Small reflective fittings make the
    # deck readable in a small three-quarter view without a texture dependency.
    rail_z = (-1.80, -1.25, -.64, .05, .72, 1.32, 1.75, 1.95)
    for side, name in ((-1, "port"), (1, "starboard")):
        tops = []
        middles = []
        for z in rail_z:
            x, y = side * max(.012, beam(z) - .025), sheer(z)
            height = .225 if z < 1.8 else .20
            model.rod(name, metal, (x, y, z), (x, y + height, z), .007, sides=7)
            tops.append((x, y + height, z))
            middles.append((x, y + height * .5, z))
        model.tube(name, metal, tops, .004, sides=6)
        model.tube(name, metal, middles, .003, sides=6)
        model.tube(name, metal, [tops[0], (side * .31, .55, -1.89),
                                (side * .1, .55, -1.90)], .007, sides=7)
        model.ellipsoid(name, red if side < 0 else green,
                        (side * .15, .61, 1.64), (.022, .018, .035), rings=5, sides=8)
        for z in (-1.64, 1.41):
            x, y = side * beam(z) * .75, sheer(z) + .035
            model.rod("deck-fittings", metal, (x, y - .02, z), (x, y + .013, z), .012)
            model.rod("deck-fittings", metal, (x, y + .014, z - .04),
                      (x, y + .014, z + .04), .009)
    # Slim helm and open spokes, with a genuine recessed cockpit underneath.
    model.rod("helm", ivory, (0, .205, -1.20), (0, .59, -1.20), .032, .021, sides=10)
    wheel = [(.12 * math.cos(i * 2 * PI / 32), .61 + .12 * math.sin(i * 2 * PI / 32), -1.25)
             for i in range(33)]
    model.tube("helm", metal, wheel, .008, sides=7)
    for angle in (PI / 2, PI * 7 / 6, PI * 11 / 6):
        model.rod("helm", metal, (0, .61, -1.25),
                  (.12 * math.cos(angle), .61 + .12 * math.sin(angle), -1.25), .0045)

    model.anchor("bow", (0, .55, 2.0))
    model.anchor("hotspot:wind", (0, 3.80, .19))
    model.anchor("hotspot:position", (0, .36, -.1))
    model.anchor("hotspot:heading", (0, .55, 1.86))
    model.anchor("hotspot:depth", (0, -1.20, -.06))
    model.anchor("hotspot:attitude", (.63, .32, -.7))
    model.write(DESTINATION)


if __name__ == "__main__":
    build()
