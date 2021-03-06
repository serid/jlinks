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
                "Stmt:2[if,then,Stmt:3[if,then,Stmt:1[0],else,Stmt:1[0]]]",
                "Stmt:3[if,then,Stmt:2[if,then,Stmt:1[0]],else,Stmt:1[0]]"
            )
        )
    }

    override fun terminals(): Array<String> = arrayOf("if", "then", "else", "0", "<eof>")

    override fun nonTerminals(): Array<String> = arrayOf("S", "Stmt")

    override val rules: String = """
        S -> Stmt <eof>
        Stmt -> 0
        Stmt -> if then Stmt
        Stmt -> if then Stmt else Stmt
        """
}