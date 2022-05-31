package jitrs.links

import jitrs.links.parser.parseOne
import jitrs.links.tablegen.generateTable
import jitrs.util.ArrayIterator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Generate table for grammar in
 * https://en.wikipedia.org/wiki/LR_parser#Grammar_for_the_example_A*2_+_1
 */
internal class ArithmeticParserTest {
    @Test
    fun testParse() {
        val scheme = getScheme()
        val rules = getRules(scheme)
        val table = generateTable(scheme, rules)

        val actual = parenthesize(scheme, rules, table, "10 + 20 * 30 + 40")
        assertEquals("((10 + (20 * 30)) + 40)", actual)
    }

    private fun getScheme() = Scheme(
        SymbolArray(
            arrayOf("<int>", "<id>", "*", "+", "<eof>"),
            arrayOf("Goal", "Sums", "Products", "Value")
        )
    )

    private fun getRules(scheme: Scheme) = metaParse(
        scheme,
        """
    Goal -> Sums <eof>
    Sums -> Sums + Products
    Sums -> Products
    Products -> Products * Value
    Products -> Value
    Value -> <int>
    Value -> <id>
"""
    )

    private fun parenthesize(scheme: Scheme, rules: Rules, table: Table, string: String): String {
        val tokens0 = tokenize(scheme.map.terminals, string)
        val tokens = ArrayIterator(tokens0)

        return cstToAst(scheme, parseOne(table, rules, tokens, true)).toString()
    }

    private fun cstToAst(scheme: Scheme, cst: Cst): Expr = when (cst) {
        is Cst.Leaf -> when (val data = cst.token.data) {
            is Token.Data.IntToken -> Expr.Num(data.data)
            is Token.Data.IdentToken -> Expr.Ident(data.data)
            else -> throw RuntimeException()
        }
        is Cst.Node -> {
            if (cst.children.size > 1) {
                val id = (cst.children[1] as? Cst.Leaf)?.token?.id!!
                val ch = scheme.map.terminals[id][0]
                Expr.Binop(ch, cstToAst(scheme, cst.children[0]), cstToAst(scheme, cst.children[2]))
            } else {
                cstToAst(scheme, cst.children[0])
            }
        }
    }

    private sealed class Expr {
        data class Num(val num: Int) : Expr() {
            override fun toString(): String = num.toString()
        }

        data class Ident(val s: String) : Expr() {
            override fun toString(): String = s
        }

        data class Binop(
            val op: Char,
            val e1: Expr,
            val e2: Expr,
        ) : Expr() {
            override fun toString(): String = "($e1 $op $e2)"
        }
    }
}


// table and scheme for https://en.wikipedia.org/wiki/LR_parser#Parse_table_for_the_example_grammar
//fun getExampleTable(): Table = Table(
//    arrayOf(
//        Row(
//            arrayOf(Action.Shift(8), Action.Shift(9), Action.Error, Action.Error, Action.Error),
//            arrayOf(-1, 1, 4, 7)
//        ),
//        Row(
//            arrayOf(Action.Error, Action.Error, Action.Error, Action.Shift(2), Action.Done),
//            arrayOf(-1, -1, -1, -1)
//        ),
//        Row(
//            arrayOf(Action.Shift(8), Action.Shift(9), Action.Error, Action.Error, Action.Error),
//            arrayOf(-1, -1, 3, 7)
//        ),
//        Row(
//            arrayOf(Action.Error, Action.Error, Action.Shift(5), Action.Reduce(1), Action.Reduce(1)),
//            arrayOf(-1, -1, -1, -1)
//        ),
//        Row(
//            arrayOf(Action.Error, Action.Error, Action.Shift(5), Action.Reduce(2), Action.Reduce(2)),
//            arrayOf(-1, -1, -1, -1)
//        ),
//        Row(
//            arrayOf(Action.Shift(8), Action.Shift(9), Action.Error, Action.Error, Action.Error),
//            arrayOf(-1, -1, -1, 6)
//        ),
//        Row(
//            arrayOf(Action.Error, Action.Error, Action.Reduce(3), Action.Reduce(3), Action.Reduce(3)),
//            arrayOf(-1, -1, -1, -1)
//        ),
//        Row(
//            arrayOf(Action.Error, Action.Error, Action.Reduce(4), Action.Reduce(4), Action.Reduce(4)),
//            arrayOf(-1, -1, -1, -1)
//        ),
//        Row(
//            arrayOf(Action.Error, Action.Error, Action.Reduce(5), Action.Reduce(5), Action.Reduce(5)),
//            arrayOf(-1, -1, -1, -1)
//        ),
//        Row(
//            arrayOf(Action.Error, Action.Error, Action.Reduce(6), Action.Reduce(6), Action.Reduce(6)),
//            arrayOf(-1, -1, -1, -1)
//        ),
//    )
//)