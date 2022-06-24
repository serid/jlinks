/**
 * Implementation of Algorithm J type inference algorithm for Hindley Milner type system.
 * https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Algorithm_J
 */

package jitrs.magma.infer

import jitrs.datastructures.*
import jitrs.util.IdentityWrapper
import jitrs.util.ensureEqual
import jitrs.util.toAlphabetChar
import kotlin.random.Random

class Inference private constructor(
    private val randomNumberProvider: Iterator<Boolean>
) {
    fun infer(expr: Expression): PolyType {
        val res = infer0(expr, PersistentList.Nil.getNil())
        return ghe(res, PersistentList.Nil.getNil())
    }

    /**
     * Algorithm J
     */
    private fun infer0(expr: Expression, bindings: Bindings): MonoTypeUnificationVar =
        when (expr) {
            is Expression.Var -> {
                // Request type of variable by index
                val sigma = bindings.asSequence().elementAt(expr.index - 1)
                instantiate(sigma)
            }
            is Expression.Application -> {
                val t0 = infer0(expr.func, bindings)
                val t1 = infer0(expr.arg, bindings)

                val newType = newUnificationVariable()
                val arrow = DisjointSetObject.new<MonoType>(MonoType.newArrow(t1, newType))

                unify(t0, arrow)

                newType
            }
            is Expression.Lambda -> {
                val t = newUnificationVariable()
                val newBindings = PersistentList.Cons(PolyType.mono(t), bindings)
                val bodyType = infer0(expr.body, newBindings)
                DisjointSetObject.new(MonoType.newArrow(t, bodyType))
            }
            is Expression.LetIn -> {
                val t = infer0(expr.func, bindings)
                val newBindings = PersistentList.Cons(ghe(t, bindings), bindings)

                @Suppress("UnnecessaryVariable")
                val bodyType = infer0(expr.body, newBindings)
                bodyType
            }
            is Expression.IfThenElse -> {
                unify(Types.intType, infer0(expr.cond, bindings))

                val bodyType = newUnificationVariable()
                unify(bodyType, infer0(expr.aye, bindings))
                unify(bodyType, infer0(expr.nay, bindings))
                bodyType
            }
            is Expression.IntConst -> {
                Types.intType
            }
        }

    private fun newUnificationVariable(): MonoTypeUnificationVar =
        DisjointSetObject.new(MonoType.Var(notUnifiedYetIndex))

    private fun unify(t0: MonoTypeUnificationVar, t1: MonoTypeUnificationVar) {
        val ta = t0.findData()
        val tb = t1.findData()

        if (ta is MonoType.Application &&
            tb is MonoType.Application &&
            ta.constructor == tb.constructor &&
            ta.args.size == tb.args.size
        )
            for (i in ta.args.indices)
                unify(ta.args[i], tb.args[i])
        else
            DisjointSetObject.union(t0, t1, ::unionResolver)
    }

    private fun unionResolver(t0: MonoType, t1: MonoType): LeftOrRight {
        // When both monotypes are type variables, it's safe to choose either one as representative
        // Randomness ensures that representation order is not relied on
        if (t0 is MonoType.Var && t1 is MonoType.Var)
            return randomNumberProvider.next()

        return when {
            t0 is MonoType.Var -> right
            t1 is MonoType.Var -> left
            else -> throw RuntimeException("Can not make union of $t0 and $t1")
        }
    }

    private fun instantiate(sigma: PolyType): MonoTypeUnificationVar {
        val forallCount = sigma.forallCount

        // Key is TypeIndex
        // Maps **bound** type variables to their freshly-allocated unification variables
        // All uses of a bound type variable should refer to the same unification variable
        val consistentReplacements = Array(forallCount) { newUnificationVariable() }

        fun visit(t: MonoTypeUnificationVar): MonoTypeUnificationVar =
            when (val t0 = t.findData()) {
                is MonoType.Var -> {
                    // Index of bound variables is never higher than forall count
                    val isVariableBound = t0.index <= forallCount && t0.index != notUnifiedYetIndex
                    if (isVariableBound) consistentReplacements[t0.index]
                    else t
                }
                is MonoType.Application -> t0.map(::visit)
            }

        return visit(sigma.mono)
    }

    // Г
    private fun ghe(t: MonoTypeUnificationVar, bindings: Bindings): PolyType {
        var counter = 0
        val map = hashMapOf<IdentityWrapper<MonoType.Var>, TypeIndex>()

        // Copy the type while binding unresolved unification variables to quantified variables
        fun visit(t1: MonoTypeUnificationVar): MonoTypeUnificationVar {
            return when (val data = t1.findData()) {
                is MonoType.Var -> {
                    // Skip variables bound in "bindings"
                    val isBound = bindings.asSequence().any { it.mono.findData() === data }
                    if (isBound)
                        return t1

                    val wrapper = IdentityWrapper(data)
                    var r = map[wrapper]
                    if (r == null) {
                        r = counter++
                        map[wrapper] = r
                    }
                    DisjointSetObject.new(MonoType.Var(r))
                }
                is MonoType.Application -> data.map(::visit)
            }
        }

        // Visit monotype
        val mono = visit(t)

        return PolyType(ensureEqual(map.size, counter), mono)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {
        fun new(): Inference {
            val x = iterator {
                while (true) {
                    yield(Random.Default.nextBoolean())
                }
            }
            return newWithRandomNumberProvider(x)
        }

        fun newWithRandomNumberProvider(randomNumberProvider: Iterator<Boolean>): Inference =
            Inference(randomNumberProvider)
    }
}

// No need to store variable key since variables are accessed using de Bruijn indices
typealias Bindings = PersistentList<PolyType>

object Types {
    val intType: MonoTypeUnificationVar by lazy { DisjointSetObject.new(MonoType.Application("Int", arrayOf())) }
    val condType = intType
}


// Internal representation of terms, has indices for names
sealed class Expression {
    data class Var(val index: Index) : Expression()

    data class Lambda(val body: Expression) : Expression()

    data class Application(val func: Expression, val arg: Expression) : Expression()

    data class LetIn(val func: Expression, val body: Expression) : Expression()

    data class IfThenElse(val cond: Expression, val aye: Expression, val nay: Expression) : Expression()

    data class IntConst(val num: Int) : Expression()

//    data class Global(val index: Index) : Expression()

    fun prettyString() = prettyString(1)

    private fun prettyString(precedence: Int): String {
        fun String.wrap(precedence0: Int) = if (precedence0 == 0) "($this)" else this
        return when (this) {
            is Var -> "x${this.index}"
            is Lambda -> "lam ${this.body.prettyString(1)}".wrap(precedence)
            is Application -> "${this.func.prettyString(1)} ${this.arg.prettyString(0)}".wrap(precedence)
            is LetIn -> ("let ${this.func.prettyString(1)} in\n" +
                    "${this.body.prettyString(1)} ").wrap(precedence)
            is IfThenElse -> ("if ${this.cond.prettyString(1)} " +
                    "then ${this.aye.prettyString(1)} " +
                    "else ${this.nay.prettyString(1)}").wrap(precedence)
            is IntConst -> "${this.num}"
//            is Global -> throw RuntimeException()
        }
    }
}


sealed class MonoType {
    data class Var(val index: TypeIndex) : MonoType() {
        override fun toString(): String =
            if (index == notUnifiedYetIndex) "-1"
            else index.toAlphabetChar().toString()
// Wrap an object in IdentityWrapper to get this behavior:

        // Var will be used as a key in hashmap and it should only compare identity
//        override fun hashCode(): Int = System.identityHashCode(this)
//
//        override fun equals(other: Any?): Boolean = this === other
    }

    data class Application(val constructor: String, val args: Array<MonoTypeUnificationVar>) : MonoType() {
        inline fun map(f: (MonoTypeUnificationVar) -> MonoTypeUnificationVar): DisjointSetObject<MonoType> =
            DisjointSetObject.new(
                Application(
                    this.constructor,
                    this.args.map { f(it) }.toTypedArray()
                )
            )

        // Generated
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Application

            if (constructor != other.constructor) return false
            if (!args.contentEquals(other.args)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = constructor.hashCode()
            result = 31 * result + args.contentHashCode()
            return result
        }

        override fun toString(): String {
            if (constructor == "->")
                return "${args[0]} -> ${args[1]}"
            if (args.isEmpty())
                return constructor
            return "($constructor ${args.joinToString(" ")})"
        }
    }

    companion object {
        fun newArrow(t0: MonoTypeUnificationVar, t1: MonoTypeUnificationVar) = Application("->", arrayOf(t0, t1))
    }
}

// MonoTypeObject is a kind of unification variable where unification result
// is accessed by calling obj.findRepresentative()
typealias MonoTypeUnificationVar = DisjointSetObject<MonoType>

data class PolyType(val forallCount: Int, val mono: MonoTypeUnificationVar) {
    override fun toString(): String {
        if (forallCount == 0)
            return "$mono"

        val binders = (0 until forallCount).joinToString(" ") { it.toAlphabetChar().toString() }
        return "forall $binders. $mono"
    }

    companion object {
        fun mono(mono: MonoTypeUnificationVar) = PolyType(0, mono)
    }
}

//sealed class PolyType {
//    data class Mono(val mono: MonoTypeUnificationVar) : PolyType()
//
//    data class Forall(val body: PolyType) : PolyType()
//}

// Variable index
typealias Index = Int
typealias TypeIndex = Int

const val notUnifiedYetIndex: TypeIndex = -1