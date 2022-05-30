package jitrs.links

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

internal class MetaParserTest {
    @Test
    fun testMetaParse() {
        val scheme = getScheme()
        val expected = getExpectedRules()
        val actual = getActualRules(scheme)

        assertContentEquals(expected, actual)
    }

    private fun getScheme() = Scheme(
        SymbolArray(
            arrayOf("*", "+", "0", "1", "<eof>"),
            arrayOf("S", "E", "B")
        )
    )

    private fun getExpectedRules() = arrayOf(
        Rule(0, arrayOf(Symbol.NonTerminal(1), Symbol.Terminal(0))),
        Rule(1, arrayOf(Symbol.NonTerminal(1), Symbol.Terminal(1), Symbol.NonTerminal(2))),
        Rule(1, arrayOf(Symbol.NonTerminal(1), Symbol.Terminal(2), Symbol.NonTerminal(2))),
        Rule(1, arrayOf(Symbol.NonTerminal(2))),
        Rule(2, arrayOf(Symbol.Terminal(3))),
        Rule(2, arrayOf(Symbol.Terminal(4)))
    )

    private fun getActualRules(scheme: Scheme) = metaParse(
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