package jitrs.magma.infer

import jitrs.datastructures.ADSData
import jitrs.datastructures.ADSIndex
import jitrs.datastructures.ArrayDisjointSet
import jitrs.magma.ir.Builtins
import jitrs.util.LateInit
import jitrs.util.cast

// Internal representation of terms. Variables refer to binders using indices but
// names are kept on binders for printing.
// Largely based on https://metacoq.github.io/metacoq/html/MetaCoq.PCUIC.PCUICAst.html#global_env
sealed class Expression {
    class Var(val index: Index) : Expression()

    class Lambda(val name: String, val annot: Type, val body: Expression) : Expression()

    class Application(val func: Expression, val arg: Expression) : Expression()

    class LetIn(val name: String, val func: Expression, val body: Expression) : Expression()

    class IfThenElse(val cond: Expression, val aye: Expression, val nay: Expression) : Expression()

    class ExVar(val id: ADSIndex) : Expression()

    // DO NOT comment-out this line under any circumstances.
    // This class represents IR, it has to be pretty-printable
    class Global(val name: KernelName) : Expression()

    // Pointer indirection could be avoided by using inheritance but that would be unintuitive
    class Builtin(val value: Builtins) : Expression()

    // Types

    class Forall(val name: String, val annot: Type, val body: Type) : Expression()

    // Type of types
    object Sort : Expression()

    // Inductives

    /**
     * An atom applied to some arguments.
     */
    class AppliedAtom(val atom: Atom, val arguments: Array<Expression>) : Expression() {
        sealed class Atom {
            data class Ind(val inductive: Inductive) : Atom()

            data class Constru(val inductive: Inductive, val id: ConstructorId) : Atom() {
                fun deref(): Constructor = this.inductive.constructors.get()[this.id]
            }
        }

        fun getType(): Type {
            val baseType = when (this.atom) {
                is Atom.Ind -> this.atom.inductive.annot
                is Atom.Constru -> this.atom.deref().type
            }
            return removeForalls(baseType, arguments.size)
        }

        fun getName(): String = when (this.atom) {
            is Atom.Ind -> this.atom.inductive.name
            is Atom.Constru -> this.atom.deref().name
        }

        companion object {
            fun removeForalls(e: Type, n: Int): Type {
                var result = e
                for (i in 0 until n) {
                    if (result !is Forall)
                        throw RuntimeException()
                    result = result.body
                }
                return result
            }

            fun ofInd(inductive: Inductive): Expression =
                AppliedAtom(Atom.Ind(inductive), arrayOf())

            fun ofConstru(inductive: Inductive, id: ConstructorId): Expression =
                AppliedAtom(Atom.Constru(inductive, id), arrayOf())
        }
    }

    class Match(
        val inductive: Inductive,
        val scrutinee: Expression,
        val branches: Array<Branch>
    ) : Expression() {
        data class Branch(val constructorId: ConstructorId, val expr: Expression)

        fun getConstructorFieldTypes(constructorId: ConstructorId): Array<Type> =
            this.inductive.constructors.get()[constructorId].fieldTypes
    }


    // // This comment does not apply anymore, but I will keep it:
    // Actually it still applies
    // Expressions are compared using object identity with
    // default implementation of [equals] and [hashCode].
    // This is why [Expression] variants are not data classes -- the only way to override
    // a data class implementation of these methods is to write it in each variant manually.

    override fun toString(): String = prettyString()

    fun prettyString() = prettyString(1)

    private fun strictPrettyString() = prettyString(0)

    private fun prettyString(precedence: Int): String {
        fun String.wrap(precedence0: Int) = if (precedence0 == 0) "($this)" else this
        return when (this) {
            is Var -> "v${this.index}"
            is Lambda -> "lam ${this.body.prettyString()}".wrap(precedence)
            is Application -> "${this.func.prettyString()} ${this.arg.strictPrettyString()}".wrap(precedence)
            is LetIn -> ("let ${this.func.prettyString()} in\n" +
                    "${this.body.prettyString()} ").wrap(precedence)

            is IfThenElse -> ("if ${this.cond.prettyString()} " +
                    "then ${this.aye.prettyString()} " +
                    "else ${this.nay.prettyString()}").wrap(precedence)

            is ExVar -> "?${this.id}"
            is Global -> this.name
            is Builtin -> this.value.toString()

            is Forall -> "forall ${this.name} : ${this.annot.prettyString()}, ${this.body.prettyString()}".wrap(
                precedence
            )

            is Sort -> "Sort"
            is AppliedAtom -> "${this.getName()} ${this.arguments.joinToString(" ")}".wrap(precedence)
            is Match -> {
                val branches = this.branches.joinToString {
                    "\n | ${this.inductive.constructors.get()[it.constructorId]} -> ${it.expr.prettyString()}"
                }
                "match ${this.scrutinee.prettyString()} with $branches end"
            }
        }
    }

    fun deepEquality(other: Expression): Boolean {
        if (this::class.java != other::class.java)
            return false

        return when (this) {
            is Builtin -> this.value == other.cast<Builtin>().value
            else -> TODO()

//            is Var -> this.index == other.cast<Var>().index
//            is Lambda -> this.annot == other.cast<Lambda>().annot ||
//                    this.body == other.cast<Lambda>().body
//            is Application -> this.func == other.cast<Application>().func ||
//                    this.arg == other.cast<Application>().arg
        }
    }

    /**
     * Run a procedure on each expression node in the tree.
     */
    inline fun visit(crossinline visitor: (current: Expression) -> Unit) {
        this.transform { visitor(it); it }
    }

    /**
     * Transform each node in the tree.
     */
    fun transform(f: (Expression) -> Expression): Expression {
        val res = f(this)
        when (res) {
            is Lambda -> {
                res.annot.transform(f)
                res.body.transform(f)
            }

            is Application -> {
                res.func.transform(f)
                res.arg.transform(f)
            }

            is LetIn -> {
                res.func.transform(f)
                res.body.transform(f)
            }

            is IfThenElse -> {
                res.cond.transform(f)
                res.aye.transform(f)
                res.nay.transform(f)
            }

            is Forall -> {
                res.annot.transform(f)
                res.body.transform(f)
            }

            is AppliedAtom -> {
                for (x in res.arguments)
                    x.transform(f)
            }

            is Match -> {
                res.scrutinee.transform(f)
                for (x in res.branches)
                    x.expr.transform(f)
            }

            is Var -> {}
            is ExVar -> {}
            is Global -> {}
            is Sort -> {}
            is Builtin -> {
                res.value.transform(f)
            }
        }
        return res
    }
}

typealias Type = Expression

// Failed experiment to make "Type" a newtype with a checking constructor
///**
// * An expression which can be used on right-hand side of type ascription (:)
// */
//data class Type private constructor(val value: Expression) {
//    companion object {
//        /**
//         * Assert that expression can be on right-hand side of (:) and box it.
//         */
//        fun fromExpression(
//            expression: Expression,
//            exVars: ExistentialVariables,
//            inference: Inference
//        ): Type {
//            inference.inferTypeOfType(expression)
//            return Type(expression)
//        }
//    }
//}

/**
 * An existential variable may not contain a value, but it should have a specified type.
 * Type of an existential variable may be an existential too, this chaining of existentials
 * can only happen once at which point type of the second existential would be
 * [Expression.Sort].
 *
 * If an existential is not assigned a value yet, it's not reduced in [Reductor.reduce].
 * Once a value for an existential is set, it cannot be changed.
 *
 * Note: when assigning a value to an existential, make sure to unify type of the value with
 * type of the existential.
 */
data class ExistentialPair(val value: Expression?, val type: Type) : ADSData<ExistentialPair> {
    override fun isSet(): Boolean = value != null

    override fun lessThan(other: ExistentialPair): Boolean {
        return if (this.value == null)
            other.value != null
        else if (other.value == null)
            false
        else
            throw RuntimeException("both values are set? or should we find representatives first?")
    }
}
// ExistentialVariables
typealias ExVariables = ArrayDisjointSet<ExistentialPair>

data class ExpressionWithExistentials(val expr: Expression, val exVars: ExVariables)

// Variable index, refers to a fun/forall/match binding
typealias Index = Int

// Datatypes for global context and inductives borrowed from
// https://metacoq.github.io/metacoq/html/MetaCoq.PCUIC.PCUICAst.html
typealias KernelName = String

typealias ConstructorId = Int

class Inductive(
    val name: KernelName,
    val annot: Type,
    val constructors: LateInit<Array<Constructor>>
) {
    // Inductives are compared using object identity with default implementation of [equals] and [hashCode]
}

data class Constructor(val name: KernelName, val type: Type) {
    val fieldTypes: Array<Type> by lazy {
        val result = arrayListOf<Type>()

        var x = type
        while (true) {
            when (x) {
                is Expression.Forall -> {
                    result.add(x.annot)
                    x = x.body
                }

                else -> break
            }
        }

        result.toTypedArray()
    }
}

data class GlobalDefinition(
    val value: Expression,
    val type: Type
)