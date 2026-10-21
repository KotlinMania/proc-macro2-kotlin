"""Require the three real ProcMacro2 Swift Testing cases and no skipped cases."""
import re
import sys
from pathlib import Path

text = re.sub(r"\x1b\[[0-9;]*m", "", Path(sys.argv[1]).read_text())
if not re.search(r"Test run with 3 tests[^\r\n]*passed", text):
    raise SystemExit("Expected all three ProcMacro2 Swift tests to pass")
if re.search(r"(?m)^.*(?:Test|Suite) .*\b(?:failed|skipped)\b", text):
    raise SystemExit("ProcMacro2 Swift test failed or was skipped")

for name, description in [
    ("tokenStreamParsingAndFormatting", "TokenStream parses valid Rust tokens and formats groups"),
    ("tokenStreamParseFailure", "TokenStream reports parse errors on invalid input"),
    ("literalAndPunctExport", "Literal and Punct bridge cleanly across Swift Export"),
]:
    label = r'(?:"' + re.escape(description) + r'"|' + re.escape(name) + r'\(\))'
    alias = r"(?: \(aka '" + re.escape(name) + r"\(\)'\))?"
    if len(re.findall(r"Test " + label + alias + r" passed\b", text)) != 1:
        raise SystemExit("Expected exactly one passing execution of " + name)
