package jitrs.links

import jitrs.links.parser.StringCst
import jitrs.util.cast
import jitrs.util.myAssert
import kotlin.test.assertEquals

internal abstract class AbstractParserTest {
    fun testParse(input: String, expectedStr: String) {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)
        myAssert(grammar.table.isUnambiguous)

        val str = grammar.parseOne(input)

        assertEquals(expectedStr, str.cast<StringCst>().string)
    }

    fun testParses(input: String, expectedStrs: Array<String>) {
        val grammar = Grammar.new(terminals(), nonTerminals(), rules)
        myAssert(!grammar.table.isUnambiguous)

        val pts = grammar.parseMany(input)

        for ((expectedStr, str) in expectedStrs.asSequence().zip(pts.asSequence()))
            assertEquals(expectedStr, str.cast<StringCst>().string)
    }

    abstract fun terminals(): Array<String>

    abstract fun nonTerminals(): Array<String>

    abstract val rules: String
}