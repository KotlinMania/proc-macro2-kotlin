package io.github.kotlinmania.procmacro2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class TokenCopyTest {
    @Test
    fun fallbackIdentifierCopyRetainsRawNameAndIndependentSpan() {
        val sourceSpan = FallbackSpan(10, 16)
        for (original in listOf(
            FallbackIdent.newUnchecked("name", sourceSpan),
            FallbackIdent.newRawUnchecked("type", sourceSpan),
        )) {
            val copied = original.clone()
            assertNotSame(original, copied)
            assertEquals(original.toString(), copied.toString())
            assertEquals(sourceSpan, copied.span())
            copied.setSpan(FallbackSpan.callSite())
            assertEquals(sourceSpan, original.span())
        }
    }

    @Test
    fun existingFallbackLiteralCopyRetainsRepresentationAndIndependentSpan() {
        val sourceSpan = FallbackSpan(20, 30)
        for (representation in listOf("-42", "0xff_u16", "r##\"raw /* text */\"##", "1.25e2f32")) {
            val original = FallbackLiteral(representation, sourceSpan)
            val copied = original.clone()
            assertNotSame(original, copied)
            assertEquals(representation, copied.toString())
            assertEquals(sourceSpan, copied.span())
            copied.setSpan(FallbackSpan.callSite())
            assertEquals(sourceSpan, original.span())
        }
    }
}
