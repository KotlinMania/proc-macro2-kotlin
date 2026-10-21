"""Require the three real ProcMacro2 Swift Testing cases and no skipped cases."""
import re
import sys
from pathlib import Path

text = re.sub(r"\x1b\[[0-9;]*m", "", Path(sys.argv[1]).read_text())
if not re.search(r"Test run with 3 tests[^\r\n]*passed", text):
    raise SystemExit("Expected all three ProcMacro2 Swift tests to pass")
if re.search(r"(?m)^.*(?:Test|Suite) .*\bskipped\b", text):
    raise SystemExit("ProcMacro2 Swift test was skipped")
