package jitrs.links

import org.junit.jupiter.api.Test

/**
 * Generate table for grammar in
 * https://en.wikipedia.org/wiki/LR_parser#Table_construction
 */
internal class TableGenTest : AbstractParserTest() {
    @Test
    fun testTablegen() {
        testParse("0 + 1 * 0", "E:1[E:2[E:3[B:4[0]],+,B:5[1]],*,B:4[0]]")
    }

    override fun getScheme() = Scheme(
        SymbolArray(
            arrayOf("*", "+", "0", "1", "<eof>"),
            arrayOf("S", "E", "B")
        )
    )

    override fun getRules(scheme: Scheme) = metaParse(
        scheme,
        """
        S -> E <eof>
        E -> E * B
        E -> E + B
        E -> B
        B -> 0
        B -> 1
        """
    )
}