/**
 * Algorithm J type inference algorithm adapted for CIC type system.
 * https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Algorithm_J
 * https://coq.inria.fr/refman/language/cic.html
 * https://coq.inria.fr/refman/language/core/conversion.html
 */

package jitrs.magma.infer

import jitrs.datastructures.*
import jitrs.magma.ir.Builtins
import jitrs.magma.syntax.Globals
import jitrs.magma.syntax.appendBinders
import jitrs.util.LateInit
import jitrs.util.cast
import kotlin.random.Random

/**
 * Infer type of an expression
 */
class Inference private constructor(
    private val globals: Globals,
    private val exVars: ExVariables,
    private val randomNumberProvider: Iterator<Boolean>,
) {
    private val resultingTypes: InferenceMapping = hashMapOf()

    /**
     * @return type of the expression and types of all subexpressions.
     */
    fun infer(expr: Expression): Pair<Type, InferenceMapping> {
//        return ghe(res, PersistentList.Nil.getNil())
        val res = infer0(expr, PersistentList.Nil.getNil())
        return Pair(enrapture(res), resultingTypes)
    }

    /**
     * Same as [infer], also assert that type of the inferred type is [Expression.Sort]
     */
    fun assertTypeOfExpressionIsSort(expr: Expression): Type {
        val (res, _) = infer(expr)
        unify(res, Expression.Sort, PersistentList.Nil.getNil())
        return res
    }

    /**
     * Algorithm J
     */
    private fun infer0(expr: Expression, bindings: Bindings): Type {
        // Check if type of this expression was already inferred
        val computed = resultingTypes[expr]
        if (computed != null)
            return computed

        val result = when (expr) {
            is Expression.Var -> {
                // Request type of variable by index
                val sigma = bindings.asSequence().elementAt(expr.index)

                // TODO: use [instantiate] for instantiating brace-implicit parameters (implicits in Coq terms)
                //                instantiate(sigma)

                // Shift free variables up since now there are new binders in context
                shift(sigma, expr.index + 1, 0)
            }

            is Expression.Application -> {
                val t0 = infer0(expr.func, bindings)
                val t1 = infer0(expr.arg, bindings)

                val returnType = allocExistentialVariable(exVars)
                val arrow = newForall(t1, returnType)

                unify(t0, arrow, bindings)

                returnType
            }

            is Expression.Lambda -> {
                val annot = expr.annot
                val newBindings = PersistentList.Cons(annot, bindings)
                val bodyType = infer0(expr.body, newBindings)
//                val bodyType1 = shiftOnExit(bodyType, 1)
                newForall(annot, bodyType)
            }

            is Expression.LetIn -> {
                val t = infer0(expr.func, bindings)
                val newBindings = PersistentList.Cons(t, bindings)
                //                val newBindings = PersistentList.Cons(ghe(t, bindings), bindings)

                val bodyType = infer0(expr.body, newBindings)

                // Shift free variables down since the type is leaving a binder
                shiftOnExit(bodyType, 1)
            }

            is Expression.IfThenElse -> {
                unify(globals["Int"]!!.value, infer0(expr.cond, bindings), bindings)

                // TODO: remove existential allocation and use infer0(expr.aye)
                val bodyType = allocExistentialVariable(exVars)
                unify(bodyType, infer0(expr.aye, bindings), bindings)
                unify(bodyType, infer0(expr.nay, bindings), bindings)
                bodyType
            }

            is Expression.ExVar -> {
                exVars.findData(expr.id).type
            }

            is Expression.Global -> {
                (globals[expr.name] ?: throw RuntimeException("\"${expr.name}\" not found in globals")).type
            }

            is Expression.Builtin -> expr.value.type()

            is Expression.Forall -> {
                Expression.Sort
            }

            is Expression.Sort -> {
                Expression.Sort // Girard's paradox
            }

            is Expression.AppliedAtom -> {
                expr.getType()
            }

            is Expression.Match -> {
                // This one is similar to inference on if-then-else expression

                val scrutineeType = infer0(expr.scrutinee, bindings)
                if (expr.inductive != ((scrutineeType as Expression.AppliedAtom).atom as Expression.AppliedAtom.Atom.Ind).inductive)
                    throw RuntimeException()

                val bodyType = allocExistentialVariable(exVars)
                for (branch in expr.branches) {
                    val constructorFieldTypes = expr.getConstructorFieldTypes(branch.constructorId)
                    val newBindings = appendBinders(bindings, constructorFieldTypes)
                    val branchType = infer0(branch.expr, newBindings)
                    // TODO: this shift might be erroneous
                    val branchType1 = shiftOnExit(branchType, constructorFieldTypes.size)
                    unify(bodyType, branchType1, newBindings)
                }
                bodyType
            }
        }

        resultingTypes[expr] = result

        return result
    }

    // Shift free variables down when a type is leaving a binding expression
    private fun shiftOnExit(type: Type, numberOfBinders: Int): Type = shift(type, -numberOfBinders, 0)

    private fun unify(ta: Expression, tb: Expression, bindings: Bindings): Unit = unify0(ta, tb, bindings)

    private fun unify0(ta0: Expression, tb0: Expression, bindings: Bindings) {
        // Arguments should be reduced to WHNF
        val ta = Reductor(globals, exVars).reduce(ta0, false)
        val tb = Reductor(globals, exVars).reduce(tb0, false)

        return when {
            ta == tb -> {}

            ta is Expression.ExVar && tb is Expression.ExVar -> {
                // When both expressions are variables, it's safe to choose either one as representative
                // Randomness ensures that representation order is not relied on
                exVars.union(ta.id, tb.id) { _, _ -> randomNumberProvider.next() }
            }

            ta is Expression.ExVar -> {
                setData(ta, tb, bindings)
            }

            tb is Expression.ExVar -> {
                setData(tb, ta, bindings)
            }

            ta is Expression.AppliedAtom &&
                    tb is Expression.AppliedAtom &&
                    ta.atom == tb.atom &&
                    ta.arguments.size == tb.arguments.size -> {
                for (i in ta.arguments.indices)
                    unify0(ta.arguments[i], tb.arguments[i], bindings)
            }

            ta is Expression.Forall &&
                    tb is Expression.Forall -> {
                unify0(ta.annot, tb.annot, bindings)

                //            val newBindings = PersistentList.Cons(ta.annot, bindings)

                // The forall argument should also be equal
                val newVar = allocExistentialVariable(exVars)
                val taBody = replace(ta.body, newVar, 0)
                val tbBody = replace(tb.body, newVar, 0)

                unify0(taBody, tbBody, bindings)
            }

            ta is Expression.Builtin && tb is Expression.Builtin &&
                    ta.value == tb.value -> {
            }

            else -> throw RuntimeException("Failed to unify $ta and $tb")
        }
    }

    private fun setData(exVar: Expression.ExVar, data: Expression, bindings: Bindings) {
        val exprType = infer0(data, bindings)
        val oldEx = exVars.findData(exVar.id)
        unify0(oldEx.type, exprType, bindings)
        exVars.setData(exVar.id, ExistentialPair(data, exprType))
    }

    private fun occursCheck(id: ADSIndex, t1: Expression): Unit = t1.visit {
        if (it is Expression.ExVar && it.id == id) throw OccursCheckException()
    }

    // Replace all foralls in head by existential variables
    /*
    private fun instantiate(sigma: PolyType): MonoTypeUnificationVar {
        val forallCount = sigma.forallCount

        // Key is TypeIndex
        // Maps **bound** type variables to their freshly-allocated unification variables
        // All uses of a bound type variable should refer to the same unification variable
        val consistentReplacements = Array(forallCount) { allocExistentialVariable() }

        fun visit(t: MonoTypeUnificationVar): MonoTypeUnificationVar =
            when (val t0 = t.findData()) {
                is MonoType.Var -> {
                    // Index of bound variables is never higher than forall count
                    val isVariableBound = t0.index <= forallCount && !isVariableNotUnifiedYet(t0.index)
                    if (isVariableBound) consistentReplacements[t0.index]
                    else t
                }
                is MonoType.Application -> t0.map(::visit)
            }

        return visit(sigma.mono)
    }
    */

    // Г
    /*
    private fun ghe(t: MonoTypeUnificationVar, bindings: Bindings): PolyType {
        var counter = 0

        // Map ununified variables to freshly chosen variable indices
        val map = hashMapOf<TypeIndex, TypeIndex>()

        // Copy the type while binding unresolved unification variables to quantified variables
        fun visit(t1: MonoTypeUnificationVar): MonoTypeUnificationVar {
            return when (val data = t1.findData()) {
                is MonoType.Var -> {
                    // Skip variables bound in "bindings"
                    val isBound = bindings.asSequence().any {
                        (it.mono.findData() as MonoType.Var) == data
                    }
                    if (isBound)
                        return t1

                    var r = map[data.index]
                    if (r == null) {
                        r = counter++
                        map[data.index] = r
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
    */

    /**
     * Traverse an expression and replace all existential variables with their assigned values.
     */
    private fun enrapture(e: Expression): Expression = e.transform {
        when (it) {
            is Expression.ExVar -> exVars.findData(it.id).value
                ?: throw RuntimeException("Can not infer this existential variable: ${it.id}")

            else -> it
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {
        fun new(globals: Globals, exVars: ExVariables): Inference {
            val x = iterator {
                while (true) {
                    yield(Random.Default.nextBoolean())
                }
            }
            return newWithRandomNumberProvider(globals, exVars, x)
        }

        fun newWithRandomNumberProvider(
            globals: Globals,
            exVars: ExVariables,
            randomNumberProvider: Iterator<Boolean>
        ): Inference {
            return Inference(globals, exVars, randomNumberProvider)
        }
    }
}

fun allocExistentialTypeVariable(exVars: ExVariables): Expression =
    Expression.ExVar(exVars.pushNewVariable(ExistentialPair(null, Expression.Sort)))

fun allocExistentialVariable(exVars: ExVariables): Expression {
    val typeVar = allocExistentialTypeVariable(exVars)
    return Expression.ExVar(exVars.pushNewVariable(ExistentialPair(null, typeVar)))
}

fun newForall(annot: Type, bodyType: Type): Type = Expression.Forall("t", annot, bodyType)

fun mkApplicationTree(func: Expression, vararg arguments: Expression): Expression {
    var res = func
    for (arg in arguments) {
        res = Expression.Application(res, arg)
    }
    return res
}

typealias InferenceMapping = HashMap<Expression, Type>

//data class InferenceResult(val expr: Expression, val map: InferenceMapping)

//fun allocExistentialVariable(exVars: ExistentialVariables, data: Expression): Expression =
//    Expression.ExVar(exVars.pushNewVariable(data))

// No need to store variable key since variables are accessed using de Bruijn indices
typealias Bindings = PersistentList<Type>


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
typealias TypeIndex = Int

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