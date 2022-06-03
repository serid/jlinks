package jitrs.links

import org.junit.jupiter.api.Test

/**
 * Generate table for grammar in
 * https://en.wikipedia.org/wiki/LALR_parser#LR_parsers
 */
internal class EmptyRuleTest : AbstractParserTest() {
    @Test
    fun testParse() {
        testParse("a a a b", "Rules:1[Many:2[a,Many:2[a,Many:2[a,Many:3[]]]],b]")
    }

    override fun terminals(): Array<String> = arrayOf("a", "b", "<eof>")

    override fun nonTerminals(): Array<String> = arrayOf("Goal", "Rules", "Many")

    override val rules: String = """
        Goal -> Rules <eof>
        Rules -> Many b
        Many -> a Many
        Many ->
        """
}