package jitrs.links

import jitrs.links.tablegen.generateTable
import jitrs.links.util.ArrayIterator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Generate table for grammar in
 * https://en.wikipedia.org/wiki/LR_parser#Table_construction
 */
internal class TableGenTest {
    @Test
    fun testTablegen() {
        val scheme = getScheme()
        val rules = getRules(scheme)
        val table = generateTable(scheme, rules)

        val tokens0 = tokenize(scheme.map.terminals, "0 + 1 * 0") { false }
        val tokens = ArrayIterator(tokens0)

        val cst = parse(table, rules, tokens, true)

        assertEquals("E[E[E[B[0]],+,B[1]],*,B[0]]", cst.toString(scheme))
    }

    private fun getScheme() = Scheme(
        SymbolArray(
            arrayOf("*", "+", "0", "1", "<eof>"),
            arrayOf("S", "E", "B")
        )
    )

    private fun getRules(scheme: Scheme) = metaParse(
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