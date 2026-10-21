"""Check the real staged root/Android publications against declared dependency pins."""
import argparse
import hashlib
import json
from pathlib import Path
import posixpath
import xml.etree.ElementTree as ET


def require(condition, message):
    if not condition:
        raise ValueError(message)


def fingerprint(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(repository, group, module, version, expected):
    directory = Path(group.replace(".", "/"))
    root = repository / directory / module / version / (module + "-" + version)
    target = repository / directory / (module + "-android") / version / (module + "-android-" + version)
    paths = {"root_module": Path(str(root) + ".module"), "root_pom": Path(str(root) + ".pom"),
             "android_module": Path(str(target) + ".module"), "android_pom": Path(str(target) + ".pom")}
    for path in paths.values():
        require(path.is_file() and not path.is_symlink(), "Missing real publication: " + str(path))
    root_metadata = json.loads(paths["root_module"].read_text())
    android_metadata = json.loads(paths["android_module"].read_text())
    for metadata in [root_metadata, android_metadata]:
        require(metadata.get("formatVersion") == "1.1", "Unsupported Gradle module metadata")
        require(all(metadata["component"].get(k) == v for k, v in
                    {"group": group, "module": module, "version": version}.items()), "Wrong owning component")
    root_url = posixpath.relpath(str(paths["root_module"]), str(paths["android_module"].parent))
    require(android_metadata["component"].get("url") == root_url, "Android metadata points to a different root")

    def dependencies(variant, required):
        selected = {}
        for edge in variant.get("dependencies", []):
            key = (edge["group"], edge["module"])
            if key not in expected:
                continue
            require(key not in selected, "Duplicate dependency: " + str(key))
            actual = edge.get("version", {}).get("requires")
            require(actual == expected[key], "Wrong dependency pin in " + variant["name"] + ": " +
                    ":".join(key) + " " + str(actual) + " != " + expected[key])
            selected[key] = actual
        require(not required or set(selected) == set(expected), "Missing required dependency pins in " + variant["name"])
        return {":".join(k): v for k, v in selected.items()}

    root_variants = root_metadata["variants"]
    common = [v for v in root_variants if v["attributes"].get("org.gradle.usage") == "kotlin-metadata" and "available-at" not in v]
    require(len(common) == 1, "Expected one root metadata dependency variant")
    root_pins = dependencies(common[0], True)
    redirects = []
    for variant in root_variants:
        if "available-at" in variant:
            edge = variant["available-at"]
            require(edge["group"] == group and edge["version"] == version, "Stale target redirect: " + variant["name"])
            if variant["attributes"].get("org.jetbrains.kotlin.platform.type") == "androidJvm" and variant["attributes"].get("org.gradle.category") == "library":
                require(edge["module"] == module + "-android", "Wrong Android target redirect")
                require(posixpath.normpath(posixpath.join(str(paths["root_module"].parent), edge["url"])) == str(paths["android_module"]), "Wrong Android target metadata URL")
                redirects.append(variant["attributes"].get("org.gradle.usage"))
    require(sorted(redirects) == ["java-api", "java-runtime"], "Missing Android API/runtime redirects")
    variants = [v for v in android_metadata["variants"] if v["attributes"].get("org.gradle.category") == "library"]
    require({v["attributes"].get("org.gradle.usage") for v in variants} == {"java-api", "java-runtime"} and len(variants) == 2, "Missing Android API/runtime variants")
    android_pins = {}
    for variant in variants:
        usage = variant["attributes"]["org.gradle.usage"]
        android_pins[usage] = dependencies(variant, usage == "java-runtime")
        files = variant.get("files", [])
        require(len(files) == 1, "Expected the real Android AAR")
        aar = target.parent / files[0]["url"]
        require(aar == Path(str(target) + ".aar") and aar.is_file(), "Wrong or missing Android AAR")
        require(aar.stat().st_size == files[0]["size"] and fingerprint(aar) == files[0]["sha256"], "Android AAR fingerprint mismatch")

    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom_pins = {}
    for name, expected_module in [("root_pom", module), ("android_pom", module + "-android")]:
        pom = ET.parse(paths[name]).getroot()
        for field, value in {"groupId": group, "artifactId": expected_module, "version": version}.items():
            require(pom.findtext("m:" + field, namespaces=ns) == value, "Wrong POM identity: " + name)
        found = {}
        for edge in pom.findall("m:dependencies/m:dependency", ns):
            edge_group = edge.findtext("m:groupId", namespaces=ns)
            edge_module = edge.findtext("m:artifactId", namespaces=ns)
            key = next((k for k in expected if k[0] == edge_group and edge_module in
                        ([k[1], k[1] + "-android"] if name == "android_pom" else [k[1]])), None)
            if key is None:
                continue
            require(key not in found, "Duplicate POM dependency")
            actual = edge.findtext("m:version", namespaces=ns)
            require(actual == expected[key], "Wrong POM dependency pin: " + name + " " + str(key) + " " + str(actual))
            found[key] = {"module": edge_module, "version": actual, "scope": edge.findtext("m:scope", default="compile", namespaces=ns)}
        require(set(found) == set(expected), "Missing POM dependencies: " + name)
        pom_pins[name] = {":".join(k): v for k, v in found.items()}
    return {"status": "passed", "coordinates": ":".join([group, module, version]),
            "files": {name: {"path": str(path), "sha256": fingerprint(path)} for name, path in paths.items()},
            "root_dependency_pins": root_pins, "android_dependency_pins": android_pins, "pom_dependency_pins": pom_pins,
            "api_scope_note": "Implementation dependencies may be absent from Android API metadata; runtime pins must match.",
            "independent_consumer": "Separate compilation/resolution required"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("--group", required=True)
    parser.add_argument("--module", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--dependency", action="append", required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--source-head", required=True)
    args = parser.parse_args()
    try:
        require(len(args.source_head) == 40 and all(c in "0123456789abcdef" for c in args.source_head), "Expected an exact source revision")
        expected = {}
        for value in args.dependency:
            group, module, version = value.split(":")
            require((group, module) not in expected, "Duplicate expected pin")
            expected[(group, module)] = version
        receipt = verify(args.repository, args.group, args.module, args.version, expected)
        receipt["source_head"] = args.source_head
    except (ValueError, KeyError, OSError, ET.ParseError) as error:
        receipt = {"status": "failed", "error": str(error)}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2))
    return 0 if receipt["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
