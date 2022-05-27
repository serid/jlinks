package jitrs.links

import jitrs.links.tablegen.generateTable
import jitrs.links.util.ArrayIterator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Generate table for grammar in
 * https://en.wikipedia.org/wiki/LALR_parser#LR_parsers
 */
internal class GeneralizedParserTest {
    @Test
    fun testParse() {
        val scheme = getScheme()
        val rules = getRules(scheme)
        val table = generateTable(scheme, rules)

        val tokens0 = tokenize(scheme, "b e c") { false }
        val tokens = ArrayIterator(tokens0)

        val cst = parse(table, rules, tokens, true)

        assertEquals( "A[b,F[e],c]", cst.toString(scheme))
    }

    private fun getScheme() = Scheme(
        SymbolArray(
            arrayOf("a", "b", "c", "d", "e", "<eof>"),
            arrayOf("S", "A", "E", "F")
        )
    )

    private fun getRules(scheme: Scheme) = metaParse(
        scheme,
        """
        S -> A <eof>
        A -> a E c
        A -> a F d
        A -> b F c
        A -> b E d
        E -> e
        F -> e
        """
    )
}