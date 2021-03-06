package jitrs.links

import org.junit.jupiter.api.Test

/**
 * Generate table for grammar in
 * https://en.wikipedia.org/wiki/LALR_parser#LR_parsers
 */
internal class GeneralizedParserTest : AbstractParserTest() {
    @Test
    fun testParse() {
        testParses("b e c", arrayOf("A:3[b,F:6[e],c]"))
    }

    override fun terminals(): Array<String> = arrayOf("a", "b", "c", "d", "e", "<eof>")

    override fun nonTerminals(): Array<String> = arrayOf("S", "A", "E", "F")

    override val rules: String = """
        S -> A <eof>
        A -> a E c
        A -> a F d
        A -> b F c
        A -> b E d
        E -> e
        F -> e
        """
}