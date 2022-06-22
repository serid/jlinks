package jitrs.links

import jitrs.util.myAssert
import kotlin.test.assertEquals

internal abstract class AbstractParserTest {
    fun testParse(input: String, expectedPt: String) {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)
        myAssert(grammar.table.isUnambiguous)

        val pt = grammar.parseOne(input)

        assertEquals(expectedPt, pt.toString(grammar.scheme))
    }

    fun testParses(input: String, expectedPts: Array<String>) {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)
        myAssert(!grammar.table.isUnambiguous)

        val pts = grammar.parseMany(input)

        for ((expectedPt, pt) in expectedPts.asSequence().zip(pts.asSequence()))
            assertEquals(expectedPt, pt.toString(grammar.scheme))
    }

    abstract fun terminals(): Array<String>

    abstract fun nonTerminals(): Array<String>

    abstract val rules: String
}