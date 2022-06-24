package jitrs.links

import jitrs.links.parser.AutoCst
import jitrs.links.parser.getContainingClassOrPackageName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class CalcTest {
    @Test
    fun calcTest() {
        val containerName = getContainingClassOrPackageName(Goal::class.java)

        val grammar = Grammar.new(terminals(), nonTerminals(), rules, containerName)

        val s = "10 + 20 * 30 + 40"
        val actual = sumsToExpr(grammar.parseOneCst(s) as Sums)
        assertEquals(650, reduce(actual))
    }

    // Intermediate tree type produced by parser

    sealed class Goal : AutoCst() {
        data class Goal1(
            val sums1: Sums
        ) : Goal()
    }

    sealed class Sums : AutoCst() {
        data class Cons(
            val sums1: Sums,
            val products1: Products
        ) : Sums()

        data class Term(
            val products1: Products
        ) : Sums()
    }

    sealed class Products : AutoCst() {
        data class Cons(
            val products1: Products,
            val int1: Int
        ) : Products()

        data class Term(
            val int1: Int
        ) : Products()
    }

    // AST type
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
            is Sums.Cons -> Expr.Expr2(sumsToExpr(ast.sums1), "+", productsToExpr(ast.products1))
            is Sums.Term -> productsToExpr(ast.products1)
        }

    private fun productsToExpr(ast: Products): Expr =
        when (ast) {
            is Products.Cons -> Expr.Expr2(productsToExpr(ast.products1), "*", Expr.Expr1(ast.int1))
            is Products.Term -> Expr.Expr1(ast.int1)
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
    Goal.Goal1 -> Sums <eof>
    Sums.Cons -> Sums + Products
    Sums.Term -> Products
    Products.Cons -> Products * <int>
    Products.Term -> <int>
"""
}











