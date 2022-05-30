package jitrs.links

import org.junit.jupiter.api.Test

/**
 * Generate table for dangling else problem.
 * https://en.wikipedia.org/wiki/Dangling_else
 *
 * Grammar is intentionally ambiguous and parser produces two fitting parse trees
 */
internal class IfThenElseTest : AbstractParserTest() {
    @Test
    fun testParse() {
        testParses(
            "if then if then 0 else 0",
            arrayOf(
                "Stmt[if,then,Stmt[if,then,Stmt[0],else,Stmt[0]]]",
                "Stmt[if,then,Stmt[if,then,Stmt[0]],else,Stmt[0]]"
            )
        )
    }

    override fun getScheme() = Scheme(
        SymbolArray(
            arrayOf("if", "then", "else", "0", "<eof>"),
            arrayOf("S", "Stmt")
        )
    )

    override fun getRules(scheme: Scheme) = metaParse(
        scheme,
        """
        S -> Stmt <eof>
        Stmt -> 0
        Stmt -> if then Stmt
        Stmt -> if then Stmt else Stmt
        """
    )
}