package jitrs.magma

import jitrs.datastructures.PersistentList
import jitrs.links.Grammar
import jitrs.links.parser.AutoCst
import jitrs.links.parser.getContainingClassOrPackageName
import jitrs.magma.infer.Expression

fun cstToIr(cst: Expr): Expression = exprToIr(cst, PersistentList.Nil())

fun exprToIr(expr: Expr, bindings: Bindings): Expression =
    when (expr) {
        is Expr.Application -> Expression.Application(exprToIr(expr.func, bindings), valueToIr(expr.arg, bindings))
        is Expr.Val -> valueToIr(expr.value, bindings)
    }

fun valueToIr(value: Value, bindings: Bindings): Expression =
    when (value) {
        is Value.Lambda -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.Lambda(exprToIr(value.body, newBindings))
        }
        is Value.LetIn -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.LetIn(
                exprToIr(value.func, bindings),
                exprToIr(value.body, newBindings)
            )
        }
        is Value.IfThenElse -> {
            Expression.IfThenElse(
                exprToIr(value.cond, bindings),
                exprToIr(value.aye, bindings),
                exprToIr(value.nay, bindings)
            )
        }
        is Value.Parens -> exprToIr(value.parenthesized, bindings)
        is Value.Num -> Expression.IntConst(value.num)
        is Value.Var -> {
            val variableNumber = bindings.asSequence().indexOf(value.name)
            // Compute de Bruijn index
            Expression.Var(variableNumber + 1)
        }
    }

typealias Bindings = PersistentList<String>
//typealias Bindings = PersistentListWithLength<String>

// Cst, has strings for names
sealed class Goal : AutoCst() {
    data class Goal1(val expr: Expr) : Goal()
}

sealed class Expr : AutoCst() {
    data class Application(val func: Expr, val arg: Value) : Expr()

    data class Val(val value: Value) : Expr()
}

sealed class Value : AutoCst() {
    data class Lambda(val name: String, val body: Expr) : Value()

    data class LetIn(val name: String, val func: Expr, val body: Expr) : Value()

    data class IfThenElse(val cond: Expr, val aye: Expr, val nay: Expr) : Value()

    data class Parens(val parenthesized: Expr) : Value()

    data class Num(val num: Int) : Value()

    data class Var(val name: String) : Value()
}

fun grammar(): Grammar {
    val containerName = getContainingClassOrPackageName(Expr::class.java)

    return Grammar.new(
        arrayOf("fun", "=>", "<ident>", "<int>", "(", ")", "let", "=", "in", "if", "then", "else", "<eof>"),
        arrayOf("Goal", "Expr", "Value"),
        rules(),
        containerName,
        { Character.isJavaIdentifierStart(it) },
        { Character.isJavaIdentifierPart(it) }
    )
}

fun rules() = """
            Goal.Goal1 -> Expr <eof>
            Expr.Application -> Expr Value
            Expr.Val -> Value
            Value.Lambda -> fun <ident> => Expr
            Value.LetIn -> let <ident> = Expr in Expr
            Value.IfThenElse -> if Expr then Expr else Expr
            Value.Parens -> ( Expr )
            Value.Num -> <int>
            Value.Var -> <ident>
            """