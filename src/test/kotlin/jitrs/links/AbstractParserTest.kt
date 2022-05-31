package jitrs.links

import jitrs.util.myAssert
import kotlin.test.assertEquals

internal abstract class AbstractParserTest {
    fun testParse(input: String, expectedCst: String) {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)

        val cst = grammar.parseOne(input)

        assertEquals(expectedCst, cst.toString(grammar.scheme))
        myAssert(grammar.table.isUnambiguous())
    }

    fun testParses(input: String, expectedCsts: Array<String>) {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)

        val csts = grammar.parse(input)

        for ((expectedCst, cst) in expectedCsts.asSequence().zip(csts.asSequence()))
            assertEquals(expectedCst, cst.toString(grammar.scheme))
        myAssert(!grammar.table.isUnambiguous())
    }

    abstract fun terminals(): Array<String>

    abstract fun nonTerminals(): Array<String>

    abstract val rules: String
}