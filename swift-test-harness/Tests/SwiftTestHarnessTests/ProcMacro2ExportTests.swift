import Testing
import ProcMacro2

@Suite("ProcMacro2 Swift Export Tests")
struct ProcMacro2ExportTests {
    @Test("Verify ProcMacro2 module imports cleanly")
    func smokeTest() {
        #expect(true)
    }
}
