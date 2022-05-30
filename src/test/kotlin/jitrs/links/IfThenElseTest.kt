package jitrs.links

import jitrs.links.tablegen.generateTable
import jitrs.links.util.ArrayIterator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Generate table for dangling else problem.
 * https://en.wikipedia.org/wiki/Dangling_else
 */
internal class IfThenElseTest {
    @Test
    fun testParse() {
        val scheme = getScheme()
        val rules = getRules(scheme)
        val table = generateTable(scheme, rules)

        println(table)

        val tokens0 = tokenize(scheme.map.terminals, "if then if then 0 else 0") { false }
        val tokens = ArrayIterator(tokens0)

        val cst = parse(table, rules, tokens, true)

        assertEquals("Stmt[if,then,Stmt[if,then,Stmt[0],else,Stmt[0]]]", cst.toString(scheme))
    }

    private fun getScheme() = Scheme(
        SymbolArray(
            arrayOf("if", "then", "else", "0", "<eof>"),
            arrayOf("S", "Stmt")
        )
    )

    private fun getRules(scheme: Scheme) = metaParse(
        scheme,
        """
        S -> Stmt <eof>
        Stmt -> 0
        Stmt -> if then Stmt
        Stmt -> if then Stmt else Stmt
        """
    )
}