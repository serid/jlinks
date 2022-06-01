package jitrs.links

import jitrs.util.myAssert
import org.junit.jupiter.api.Test

/**
 * Generate table for grammar in
 * https://en.wikipedia.org/wiki/LALR_parser#LR_parsers
 */
internal class EmptyRuleTest : AbstractParserTest() {
    @Test
    fun testParse() {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)
        myAssert(grammar.table.isUnambiguous)

        val cst = grammar.parseOne("a a a b")
        println(cst.toString(grammar.scheme))
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