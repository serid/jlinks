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