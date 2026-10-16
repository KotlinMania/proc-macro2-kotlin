#if canImport(Testing)
import Testing
import ProcMacro2

@Suite("ProcMacro2 Swift Export Tests")
struct ProcMacro2ExportTests {
    @Test("TokenStream parses valid Rust tokens and formats groups")
    func tokenStreamParsingAndFormatting() {
        let span = Span.Companion.shared.callSite()
        let ident = Ident.Companion.shared.new(string: "x", span: span)
        #expect(ident.toString() == "x")

        let rawIdent = Ident.Companion.shared.newRaw(string: "type", span: span)
        #expect(rawIdent.toString() == "r#type")

        let inner = TokenStream.Companion.shared.fromTokenTree(token: TokenTree.Ident(value: ident))
        #expect(!inner.isEmpty())

        let parens = Group(delimiter: .Parenthesis, stream: inner)
        #expect(parens.delimiter() == .Parenthesis)
        #expect(parens.toString() == "(x)")

        let braces = Group(delimiter: .Brace, stream: inner)
        #expect(braces.delimiter() == .Brace)
        #expect(braces.toString() == "{ x }")

        let parsed = TokenStream.Companion.shared.fromString(src: "fn main() {}")
        #expect(parsed.isSuccess())
        #expect(parsed.error == nil)
        #expect(parsed.value?.isEmpty() == false)
    }

    @Test("TokenStream reports parse errors on invalid input")
    func tokenStreamParseFailure() {
        let invalid = TokenStream.Companion.shared.fromString(src: "unclosed (")
        #expect(invalid.isFailure())
        #expect(invalid.value == nil)
        #expect(invalid.error != nil)
    }

    @Test("Literal and Punct bridge cleanly across Swift Export")
    func literalAndPunctExport() {
        let strLit = Literal.Companion.shared.string(string: "hello")
        #expect(strLit.toString() == "\"hello\"")
        let unescaped = strLit.strValue()
        #expect(unescaped.isSuccess())
        #expect(unescaped.value == "hello")

        let intLit = Literal.Companion.shared.i32Suffixed(n: 42)
        #expect(intLit.toString() == "42i32")

        let span = Span.Companion.shared.callSite()
        let punct = Punct(ch: 58, spacing: .Joint, span: span)
        #expect(punct.asChar() == 58)
        #expect(punct.spacing() == .Joint)
        #expect(punct.toString() == ":")
    }
}
#elseif canImport(XCTest)
import XCTest
import ProcMacro2

final class ProcMacro2ExportTests: XCTestCase {
    func testTokenStreamParsingAndFormatting() {
        let span = Span.Companion.shared.callSite()
        let ident = Ident.Companion.shared.new(string: "x", span: span)
        XCTAssertEqual(ident.toString(), "x")

        let inner = TokenStream.Companion.shared.fromTokenTree(token: TokenTree.Ident(value: ident))
        XCTAssertFalse(inner.isEmpty())

        let parens = Group(delimiter: .Parenthesis, stream: inner)
        XCTAssertEqual(parens.delimiter(), .Parenthesis)
        XCTAssertEqual(parens.toString(), "(x)")

        let parsed = TokenStream.Companion.shared.fromString(src: "fn main() {}")
        XCTAssertTrue(parsed.isSuccess())
        XCTAssertNil(parsed.error)
        XCTAssertEqual(parsed.value?.isEmpty(), false)
    }

    func testTokenStreamParseFailure() {
        let invalid = TokenStream.Companion.shared.fromString(src: "unclosed (")
        XCTAssertTrue(invalid.isFailure())
        XCTAssertNil(invalid.value)
        XCTAssertNotNil(invalid.error)
    }
}
#endif
