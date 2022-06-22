package jitrs.links

import jitrs.links.parser.AutoAst
import jitrs.links.parser.CstToAst
import jitrs.links.parser.getContainingClassOrPackageName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class CalcTest {
    @Test
    fun calcTest() {
        val containerName = getContainingClassOrPackageName(Expr::class.java)

        val grammar = Grammar.new(terminals(), nonTerminals(), rules)

        val treeRewriter = CstToAst.new(grammar.scheme, grammar.rules, containerName)

        val s = "10 + 20 * 30 + 40"
        val cst = grammar.parseOne(s)
        val actual = sumsToExpr(treeRewriter.cstToAst(cst as Cst.Node) as Sums)
        assertEquals(650, reduce(actual))
    }

    // Intermediate tree type produced by parser

    sealed class Goal : AutoAst() {
        data class Goal1(
            val sums1: Sums
        ) : Goal()
    }

    sealed class Sums : AutoAst() {
        data class Sums1(
            val sums1: Sums,
            val products1: Products
        ) : Sums()

        data class Sums2(
            val products1: Products
        ) : Sums()
    }

    sealed class Products : AutoAst() {
        data class Products1(
            val products1: Products,
            val int1: Int
        ) : Products()

        data class Products2(
            val int1: Int
        ) : Products()
    }

    // Last AST type
    sealed class Expr {
        data class Expr1(
            val int1: Int
        ) : Expr()

        data class Expr2(
            val expr1: Expr,
            val str2: String,
            val expr3: Expr
        ) : Expr()
    }

    private fun goalToExpr(ast: Goal): Expr =
        sumsToExpr((ast as Goal.Goal1).sums1)

    private fun sumsToExpr(ast: Sums): Expr =
        when (ast) {
            is Sums.Sums1 -> Expr.Expr2(sumsToExpr(ast.sums1), "+", productsToExpr(ast.products1))
            is Sums.Sums2 -> productsToExpr(ast.products1)
        }

    private fun productsToExpr(ast: Products): Expr =
        when (ast) {
            is Products.Products1 -> Expr.Expr2(productsToExpr(ast.products1), "*", Expr.Expr1(ast.int1))
            is Products.Products2 -> Expr.Expr1(ast.int1)
        }

    private fun reduce(expr: Expr): Int =
        when (expr) {
            is Expr.Expr1 -> expr.int1
            is Expr.Expr2 -> when (expr.str2) {
                "+" -> reduce(expr.expr1) + reduce(expr.expr3)
                "*" -> reduce(expr.expr1) * reduce(expr.expr3)
                else -> throw RuntimeException()
            }
        }

    private fun terminals(): Array<String> = arrayOf("<int>", "*", "+", "<eof>")

    private fun nonTerminals(): Array<String> = arrayOf("Goal", "Sums", "Products")

    private val rules: String =
        """
    Goal -> Sums <eof>
    Sums -> Sums + Products
    Sums -> Products
    Products -> Products * <int>
    Products -> <int>
"""
}











