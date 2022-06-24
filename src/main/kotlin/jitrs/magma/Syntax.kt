package jitrs.magma

import jitrs.datastructures.PersistentList
import jitrs.links.parser.AutoCst
import jitrs.magma.infer.Expression

fun cstToIr(cst: Expr): Expression = exprToIr(cst, PersistentList.Nil())

fun exprToIr(expr: Expr, bindings: Bindings): Expression =
    when (expr) {
        is Expr.Expr1 -> Expression.Application(exprToIr(expr.func, bindings), valueToIr(expr.arg, bindings))
        is Expr.Expr2 -> valueToIr(expr.value, bindings)
    }

fun valueToIr(value: Value, bindings: Bindings): Expression =
    when (value) {
        is Value.Value1 -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.Lambda(exprToIr(value.body, newBindings))
        }
        is Value.Value2 -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.LetIn(
                exprToIr(value.func, bindings),
                exprToIr(value.body, newBindings)
            )
        }
        is Value.Value3 -> {
            Expression.IfThenElse(
                exprToIr(value.cond, bindings),
                exprToIr(value.aye, bindings),
                exprToIr(value.nay, bindings)
            )
        }
        is Value.Value4 -> exprToIr(value.parenthesized, bindings)
        is Value.Value5 -> Expression.IntConst(value.num)
        is Value.Value6 -> {
//            val size = bindings.size()
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
    data class Expr1(val func: Expr, val arg: Value) : Expr()

    data class Expr2(val value: Value) : Expr()
}

sealed class Value : AutoCst() {
    data class Value1(val name: String, val body: Expr) : Value()

    data class Value2(val name: String, val func: Expr, val body: Expr) : Value()

    data class Value3(val cond: Expr, val aye: Expr, val nay: Expr) : Value()

    data class Value4(val parenthesized: Expr) : Value()

    data class Value5(val num: Int) : Value()

    data class Value6(val name: String) : Value()
}

fun rules() = """
            Goal -> Expr <eof>
            Expr -> Expr Value
            Expr -> Value
            Value -> fun <ident> => Expr
            Value -> let <ident> = Expr in Expr
            Value -> if Expr then Expr else Expr
            Value -> ( Expr )
            Value -> <int>
            Value -> <ident>
            """