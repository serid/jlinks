package jitrs.magma.syntax

import jitrs.datastructures.PersistentList
import jitrs.links.Grammar
import jitrs.links.parser.AutoCst
import jitrs.links.parser.getContainingClassOrPackageName
import jitrs.magma.infer.Expression

fun cstToIr(cst: Expr): Expression = exprToIr(cst, PersistentList.Nil())

fun exprToIr(expr: Expr, bindings: Bindings): Expression =
    when (expr) {
        is Expr.Application -> Expression.Application(exprToIr(expr.func, bindings), valueToIr(expr.arg, bindings))
        is Expr.Just -> valueToIr(expr.value, bindings)
    }

fun valueToIr(value: Val, bindings: Bindings): Expression =
    when (value) {
        is Val.Lambda -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.Lambda(exprToIr(value.body, newBindings))
        }
        is Val.LetIn -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.LetIn(
                exprToIr(value.func, bindings),
                exprToIr(value.body, newBindings)
            )
        }
        is Val.IfThenElse -> {
            Expression.IfThenElse(
                exprToIr(value.cond, bindings),
                exprToIr(value.aye, bindings),
                exprToIr(value.nay, bindings)
            )
        }
        is Val.Parens -> exprToIr(value.parenthesized, bindings)
        is Val.Num -> Expression.IntConst(value.num)
        is Val.Var -> {
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
    data class Application(val func: Expr, val arg: Val) : Expr()

    data class Just(val value: Val) : Expr()
}

sealed class Val : AutoCst() {
    data class Lambda(val name: String, val body: Expr) : Val()

    data class LetIn(val name: String, val func: Expr, val body: Expr) : Val()

    data class IfThenElse(val cond: Expr, val aye: Expr, val nay: Expr) : Val()

    data class Parens(val parenthesized: Expr) : Val()

    data class Num(val num: Int) : Val()

    data class Var(val name: String) : Val()
}

fun grammar(): Grammar {
    val containerName = getContainingClassOrPackageName(Expr::class.java)

    return Grammar.new(
        arrayOf("fun", "=>", "<ident>", "<int>", "(", ")", "let", "=", "in", "if", "then", "else", "<eof>"),
        arrayOf("Goal", "Expr", "Val"),
        rules(),
        containerName,
        { Character.isJavaIdentifierStart(it) },
        { Character.isJavaIdentifierPart(it) }
    )
}

fun rules() = """
            -- Goal is ignored
            Goal.Goal1 -> Expr <eof>
            
            Expr.Application -> Expr Val
            Expr.Just -> Val
            Val.Lambda -> fun <ident> => Expr
            Val.LetIn -> let <ident> = Expr in Expr
            Val.IfThenElse -> if Expr then Expr else Expr
            Val.Parens -> ( Expr )
            Val.Num -> <int>
            Val.Var -> <ident>
            """