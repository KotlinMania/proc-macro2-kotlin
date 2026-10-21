"""Exercise stale-metadata rejection against a real staged publication."""
import argparse
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from verify_publication_pins import verify


class PublicationPinsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=Path.cwd())
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name)
        prefix = Path(options.group.replace(".", "/"))
        self.root = self.repository / prefix / options.module / options.version / (options.module + "-" + options.version)
        self.android = self.repository / prefix / (options.module + "-android") / options.version / (options.module + "-android-" + options.version)
        for base, suffixes in [(self.root, [".module", ".pom"]), (self.android, [".module", ".pom", ".aar"])]:
            for suffix in suffixes:
                destination = Path(str(base) + suffix)
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(options.repository / destination.relative_to(self.repository), destination)

    def check(self):
        return verify(self.repository, options.group, options.module, options.version, expected)

    def stale_module(self, base, usage):
        path = Path(str(base) + ".module")
        metadata = json.loads(path.read_text())
        variant = next(v for v in metadata["variants"] if v.get("attributes", {}).get("org.gradle.usage") == usage and v.get("dependencies"))
        edge = next(d for d in variant["dependencies"] if (d["group"], d["module"]) == reviewed_edge)
        edge["version"]["requires"] = "0.0.0-stale"
        path.write_text(json.dumps(metadata))

    def test_actual_publication_accepts_implementation_scope(self):
        self.assertEqual(self.check()["status"], "passed")

    def test_stale_root_module_is_rejected(self):
        self.stale_module(self.root, "kotlin-metadata")
        with self.assertRaisesRegex(ValueError, "Wrong dependency pin"):
            self.check()

    def test_stale_android_runtime_module_is_rejected(self):
        self.stale_module(self.android, "java-runtime")
        with self.assertRaisesRegex(ValueError, "Wrong dependency pin"):
            self.check()

    def test_stale_root_pom_is_rejected(self):
        path = Path(str(self.root) + ".pom")
        text = path.read_text()
        current = expected[reviewed_edge]
        self.assertIn("<version>" + current + "</version>", text)
        path.write_text(text.replace("<version>" + current + "</version>", "<version>0.0.0-stale</version>"))
        with self.assertRaisesRegex(ValueError, "Wrong POM dependency pin"):
            self.check()

    def test_stale_android_pom_is_rejected(self):
        path = Path(str(self.android) + ".pom")
        current = expected[reviewed_edge]
        path.write_text(path.read_text().replace("<version>" + current + "</version>", "<version>0.0.0-stale</version>"))
        with self.assertRaisesRegex(ValueError, "Wrong POM dependency pin"):
            self.check()

    def test_stale_target_redirect_is_rejected(self):
        path = Path(str(self.root) + ".module")
        metadata = json.loads(path.read_text())
        next(v for v in metadata["variants"] if "available-at" in v)["available-at"]["version"] = "0.0.0-stale"
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "Stale target redirect"):
            self.check()

    def test_changed_aar_is_rejected(self):
        path = Path(str(self.android) + ".aar")
        path.write_bytes(path.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "fingerprint mismatch"):
            self.check()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("--group", required=True)
    parser.add_argument("--module", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--dependency", action="append", required=True)
    options = parser.parse_args()
    expected = {(parts[0], parts[1]): parts[2] for parts in [value.split(":") for value in options.dependency]}
    reviewed_edge = ("io.github.kotlinmania", "proc-macro-kotlin")
    if reviewed_edge not in expected:
        raise ValueError("Supply the actual Proc Macro dependency pin")
    unittest.main(argv=[__file__], verbosity=2)
