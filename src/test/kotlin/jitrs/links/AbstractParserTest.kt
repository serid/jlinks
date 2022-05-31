package jitrs.links

import jitrs.links.parser.parse
import jitrs.links.parser.parseOne
import jitrs.links.tablegen.generateTable
import jitrs.util.ArrayIterator
import jitrs.util.myAssert
import kotlin.test.assertEquals

internal abstract class AbstractParserTest {
    fun testParse(input: String, expectedCst: String) {
        val scheme = getScheme()
        val rules = getRules(scheme)
        val table = generateTable(scheme, rules)

        val tokens0 = tokenize(scheme, input)
        val tokens = ArrayIterator(tokens0)

        val cst = parseOne(table, rules, tokens, true)

        assertEquals(expectedCst, cst.toString(scheme))
        myAssert(table.isUnambiguous())
    }

    fun testParses(input: String, expectedCsts: Array<String>) {
        val scheme = getScheme()
        val rules = getRules(scheme)
        val table = generateTable(scheme, rules)

        val tokens0 = tokenize(scheme, input)
        val tokens = ArrayIterator(tokens0)

        val csts = parse(table, rules, tokens, returnFirstParse = false, debug = true)

        for ((expectedCst, cst) in expectedCsts.asSequence().zip(csts.asSequence()))
            assertEquals(expectedCst, cst.toString(scheme))
        myAssert(!table.isUnambiguous())
    }

    abstract fun getScheme(): Scheme

    abstract fun getRules(scheme: Scheme): Rules
}