#!/usr/bin/env python3
"""
Static sanity checks for the Android project (no Android SDK needed). Catches the mistakes
that most often break a Gradle build:
  * every R.string / R.drawable / R.color / R.id / R.plurals / R.mipmap used in Kotlin exists
  * every @string/@drawable/@color/@style/@menu/@xml/@mipmap used in XML exists
  * every ViewBinding property (binding.xyz) used in an Activity exists in its layout
  * en / af / xh string files define exactly the same keys
  * every XML file is well formed
Run from the project root:  python3 tools/check_android_resources.py
"""
import glob, os, re, sys
import xml.dom.minidom as md

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "android/app/src/main")
RES = os.path.join(APP, "res")
errors = []

# ---- well-formedness
for f in glob.glob(RES + "/**/*.xml", recursive=True) + [APP + "/AndroidManifest.xml"]:
    try:
        md.parse(f)
    except Exception as e:
        errors.append("XML not well-formed: %s (%s)" % (os.path.relpath(f, ROOT), e))

# ---- collect defined resources
defined = {k: set() for k in ["string", "plurals", "drawable", "color", "id", "style", "menu", "xml", "mipmap", "layout"]}
def names_in_values(tag, kind):
    for f in glob.glob(RES + "/values*/*.xml"):
        for n in md.parse(f).getElementsByTagName(tag):
            if n.getAttribute("name"):
                defined[kind].add(n.getAttribute("name").replace(".", "_") if kind == "style" else n.getAttribute("name"))
names_in_values("string", "string"); names_in_values("plurals", "plurals")
names_in_values("color", "color"); names_in_values("style", "style")
for kind, folders in {"drawable": ["drawable*"], "color": ["color*"], "menu": ["menu"], "xml": ["xml"], "mipmap": ["mipmap*"], "layout": ["layout*"]}.items():
    for pat in folders:
        for f in glob.glob(os.path.join(RES, pat, "*")):
            if os.path.isfile(f):
                defined[kind].add(os.path.splitext(os.path.basename(f))[0])
for f in glob.glob(RES + "/**/*.xml", recursive=True):
    txt = open(f, encoding="utf-8").read()
    for m in re.finditer(r'@\+id/([A-Za-z0-9_]+)', txt):
        defined["id"].add(m.group(1))
for n in ["app_name"]:
    pass

# ---- R.x.y usage in Kotlin
for f in glob.glob(APP + "/java/**/*.kt", recursive=True):
    txt = open(f, encoding="utf-8").read()
    for m in re.finditer(r'(?<!android\.)\bR\.(string|plurals|drawable|color|id|style|menu|xml|mipmap|layout)\.([A-Za-z0-9_]+)', txt):
        kind, name = m.group(1), m.group(2)
        if name not in defined[kind]:
            errors.append("%s: R.%s.%s is not defined" % (os.path.relpath(f, ROOT), kind, name))

# ---- @x/y usage in XML
for f in glob.glob(RES + "/**/*.xml", recursive=True) + [APP + "/AndroidManifest.xml"]:
    txt = open(f, encoding="utf-8").read()
    for m in re.finditer(r'@(string|plurals|drawable|color|style|menu|xml|mipmap|layout|id)/([A-Za-z0-9_.]+)', txt):
        kind, name = m.group(1), m.group(2).replace(".", "_") if m.group(1) == "style" else m.group(2)
        if kind == "id":
            continue  # @id/ may be forward references to @+id definitions (checked above)
        if kind == "style" and name.startswith(("Widget_Material3", "Theme_Material3", "TextAppearance", "ThemeOverlay", "Widget_MaterialComponents", "ShapeAppearance_Material3")):
            continue
        if name not in defined[kind]:
            errors.append("%s: @%s/%s is not defined" % (os.path.relpath(f, ROOT), kind, name))
    for m in re.finditer(r'@id/([A-Za-z0-9_]+)', txt):
        if m.group(1) not in defined["id"]:
            errors.append("%s: @id/%s is not defined" % (os.path.relpath(f, ROOT), m.group(1)))

# ---- ViewBinding property usage
def camel(s):
    parts = s.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])
layout_ids = {}
for f in glob.glob(RES + "/layout/*.xml"):
    ids = set(re.findall(r'android:id="@\+id/([A-Za-z0-9_]+)"', open(f, encoding="utf-8").read()))
    layout_ids[os.path.basename(f)[:-4]] = {camel(i) for i in ids}
def binding_class(layout):
    return "".join(p.capitalize() for p in layout.split("_")) + "Binding"
class_to_layout = {binding_class(l): l for l in layout_ids}
for f in glob.glob(APP + "/java/**/*.kt", recursive=True):
    txt = open(f, encoding="utf-8").read()
    for cls, layout in class_to_layout.items():
        # variable names holding that binding type
        names = set(re.findall(r'(?:private\s+)?(?:lateinit\s+)?va[lr]\s+(\w+)\s*:\s*%s\b' % cls, txt))
        names |= set(re.findall(r'va[lr]\s+(\w+)\s*=\s*%s\.inflate' % cls, txt))
        for n in names:
            for m in re.finditer(r'\b%s\.([A-Za-z0-9_]+)' % re.escape(n), txt):
                prop = m.group(1)
                if prop in ("root",):
                    continue
                if prop not in layout_ids[layout]:
                    errors.append("%s: %s.%s not found in %s.xml" % (os.path.relpath(f, ROOT), n, prop, layout))

# ---- locale parity
def keys(folder):
    d = md.parse(os.path.join(RES, folder, "strings.xml"))
    return ({n.getAttribute("name") for n in d.getElementsByTagName("string")},
            {n.getAttribute("name") for n in d.getElementsByTagName("plurals")})
base = keys("values")
for loc in ("values-af", "values-xh"):
    other = keys(loc)
    for i, label in enumerate(("string", "plurals")):
        for k in sorted(base[i] - other[i]): errors.append("%s is missing %s '%s'" % (loc, label, k))
        for k in sorted(other[i] - base[i]): errors.append("%s has extra %s '%s'" % (loc, label, k))

# ---- strings used nowhere (informational)
if errors:
    print("PROBLEMS FOUND (%d):" % len(errors))
    for e in errors: print("  -", e)
    sys.exit(1)
print("All static Android checks passed: XML valid, resources resolved, view bindings match, languages in sync.")
