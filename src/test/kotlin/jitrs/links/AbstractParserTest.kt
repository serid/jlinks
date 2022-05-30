package jitrs.links

import jitrs.links.tablegen.generateTable
import jitrs.links.util.ArrayIterator
import kotlin.test.assertEquals

internal abstract class AbstractParserTest {
    fun testParse(input: String, expectedCst: String) {
        val scheme = getScheme()
        val rules = getRules(scheme)
        val table = generateTable(scheme, rules)

        val tokens0 = tokenize(scheme.map.terminals, input) { false }
        val tokens = ArrayIterator(tokens0)

        val cst = parse(table, rules, tokens, true)

        assertEquals(expectedCst, cst.toString(scheme))
    }

    abstract fun getScheme(): Scheme

    abstract fun getRules(scheme: Scheme): Rules
}