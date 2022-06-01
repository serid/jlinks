package jitrs.links

import jitrs.util.myAssert
import kotlin.test.assertEquals

internal abstract class AbstractParserTest {
    fun testParse(input: String, expectedCst: String) {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)
        myAssert(grammar.table.isUnambiguous)

        val cst = grammar.parseOne(input)

        assertEquals(expectedCst, cst.toString(grammar.scheme))
    }

    fun testParses(input: String, expectedCsts: Array<String>) {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)
        myAssert(!grammar.table.isUnambiguous)

        val csts = grammar.parse(input)

        for ((expectedCst, cst) in expectedCsts.asSequence().zip(csts.asSequence()))
            assertEquals(expectedCst, cst.toString(grammar.scheme))
    }

    abstract fun terminals(): Array<String>

    abstract fun nonTerminals(): Array<String>

    abstract val rules: String
}