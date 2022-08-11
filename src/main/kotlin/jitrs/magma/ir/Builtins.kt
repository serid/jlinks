package jitrs.magma.ir

import jitrs.magma.infer.Expression
import jitrs.magma.infer.Type
import jitrs.magma.infer.mkApplicationTree
import jitrs.magma.infer.newForall
import jitrs.util.UnreachableError

//val pair1Type: Lazy<Type> = lazy { ExpressionCompiler().getIrNoExistentials(
//    "forall x : Sort, forall y : Sort, x -> y -> Pair x y") }
//val pair2Type: Lazy<Type> = lazy { ExpressionCompiler().getIrNoExistentials(
//    "forall z : Sort, z -> Pair x y") }

sealed class Builtins {
    // Int
    object FInt : Builtins()

    data class IntConst(val n: Int) : Builtins() {
        override fun toString(): String {
            return super.toString()
        }
    }

    data class BinaryOperation(val op: Op) : Builtins() {
        enum class Op {
            Plus, Mul, Eq
        }

        override fun toString(): String {
            return super.toString()
        }
    }

    // Pair
//    object Pair : Builtins()
//
//    // Unapplied constructor
//    object Pair1 : Builtins()
//
//    // Constructor with one known argument
//    object Pair2 : Builtins()
//
//    object PairL : Builtins()
//
//    object PairR : Builtins()


    fun type(): Type = when (this) {
        is FInt -> Expression.Sort
        is IntConst -> Expression.Builtin(FInt)

        is BinaryOperation -> when (op) {
            BinaryOperation.Op.Plus -> newForall(
                mkApplicationTree(pair, Expression.Builtin(FInt), Expression.Builtin(FInt)),
                Expression.Builtin(FInt)
            )
            BinaryOperation.Op.Mul -> newForall(
                mkApplicationTree(pair, Expression.Builtin(FInt), Expression.Builtin(FInt)),
                Expression.Builtin(FInt)
            )
            BinaryOperation.Op.Eq -> newForall(
                mkApplicationTree(pair, Expression.Builtin(FInt), Expression.Builtin(FInt)),
                bool
            )
        }
    }

    /**
     * Apply a builtin to an argument
     */
    fun apply(arg: Expression, reduce: (Expression) -> Expression): Expression = when (this) {
        is BinaryOperation -> {
            val pair = reduce(arg) as Expression.AppliedAtom
            pair.atom as Expression.AppliedAtom.Atom.Constru

            // This should be caught by the typechecker
//            if (pair.atom.inductive !=
//                ((reduce(pair) as Expression.AppliedAtom).atom as Expression.AppliedAtom.Atom.Ind).inductive
//            )
//                throw RuntimeException("Expected a Pair")
//            if (pair.atom.id != 0)
//                throw RuntimeException("Pair should have 1 constructor")

            val v1 = ((pair.arguments[2] as Expression.Builtin).value as IntConst)
            val v2 = ((pair.arguments[3] as Expression.Builtin).value as IntConst)
            when (this.op) {
                BinaryOperation.Op.Plus -> Expression.Builtin(IntConst(v1.n + v2.n))
                BinaryOperation.Op.Mul -> Expression.Builtin(IntConst(v1.n * v2.n))
                BinaryOperation.Op.Eq -> booleanToBool(v1.n == v2.n)
            }
        }

        else -> throw UnreachableError()
    }

    override fun toString(): String = when (this) {
        is FInt -> "int"
        is IntConst -> "${this.n}"
        is BinaryOperation -> when (this.op) {
            BinaryOperation.Op.Plus -> "int_plus"
            BinaryOperation.Op.Mul -> "int_mul"
            BinaryOperation.Op.Eq -> "int_eq"
        }
    }

    fun transform(f: (Expression) -> Expression) {
        when (this) {
            is FInt -> {}
            is IntConst -> {}
            is BinaryOperation -> {}
        }
    }

    // Expect these builtins to be defined by the user
    // TODO: make a "builtins" module
    companion object {
        val bool = Expression.Global("Bool")
        val true_ = Expression.Global("true")
        val false_ = Expression.Global("false")
        val pair = Expression.Global("Pair")
        val pairC = Expression.Global("pair")
        val pairL = Expression.Global("pairL")
        val pairR = Expression.Global("pairR")

        // This leads to a cyclic dependency and java.lang.NoClassDefFoundError
//        val int_ = Expression.Builtin(FInt)

        val intPlus = BinaryOperation(BinaryOperation.Op.Plus)
        val intMul = BinaryOperation(BinaryOperation.Op.Mul)
        val intEq = BinaryOperation(BinaryOperation.Op.Eq)
    }
}

fun booleanToBool(boolean: Boolean): Expression = if (boolean) Builtins.true_ else Builtins.false_