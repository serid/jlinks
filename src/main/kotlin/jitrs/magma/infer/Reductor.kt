package jitrs.magma.infer

import jitrs.magma.syntax.Globals
import jitrs.util.myAssert
import jitrs.util.nullableMap

class Reductor(
    private val globals: Globals,
    private val exVars: ExVariables
) {
    /**
     * Reduce an expression to a normal form.
     * Apply beta, delta, iota and zeta reductions until there are no redexes,
     * this function applies reductions inside lambdas and foralls and implements call-by-value.
     * See file start for reductions explanation.
     *
     * @param fullReduction if this is false, lazily reduce to weak head-normal form (WHNF), uses call-by-name
     *
     * Note 2: there is an optimized version of beta reduction that does not require shifting,
     * but it only works in absence of free variables -- that is it works for toplevel reductions,
     * which means it won't work inside Forall expressions. Although we could use this reduction
     * strategy for expressions which are not nested in a Forall.
     */
    fun reduce(e: Expression, fullReduction: Boolean): Expression {
        return when (e) {
            is Expression.Application -> {
                val func = reduce(e.func, fullReduction)
                val arg = reduceIfEnergetic(e.arg, fullReduction)

                when (func) {
                    // Add new field to the applied atom
                    is Expression.AppliedAtom ->
                        Expression.AppliedAtom(func.atom, func.arguments.plusElement(arg))
                    // Beta reduction for lambda application
                    is Expression.Lambda -> reduce(shiftAndReplace(func.body, arg), fullReduction)
                    // Implement builtins
                    is Expression.Builtin -> func.value.apply(arg) { reduce(it, fullReduction) }

                    // Since we may be reducing inside a Forall, func may be a variable
                    // or other non-reducible expression
                    else -> e
                    // else -> throw RuntimeException("expected a lambda or a constructor tree")
                }
            }

            // Zeta reduction for let-in
            is Expression.LetIn -> {
                val func = reduceIfEnergetic(e.func, fullReduction)
                reduce(shiftAndReplace(e.body, func), fullReduction)
            }

            // Delta reduction for global variables
            is Expression.Global -> {
                reduce(globals[e.name]!!.value, fullReduction)
            }

            // Delta reduction for existential variables
            is Expression.ExVar -> {
                val (value, _type) = exVars.findData(e.id)
                // If ExVar is not assigned yet, don't reduce
                nullableMap(value) { reduce(it, fullReduction) } ?: e
            }

            // Iota reduction for match on a constructor
            is Expression.Match -> {
                val scrutinee = reduce(e.scrutinee, fullReduction)
                if (scrutinee !is Expression.AppliedAtom) {
                    println("match reduction blocked by a scrutinee, is this bad?")
                    return Expression.Match(e.inductive, scrutinee, e.branches)
                }
                val constructor = scrutinee.atom
                val fields = scrutinee.arguments
                constructor as Expression.AppliedAtom.Atom.Constru

                // Find a matching branch
                val branch = e.branches.find { it.constructorId == constructor.id }!!
                val constructorFieldsNumber = e.getConstructorFieldTypes(constructor.id).size

                myAssert(fields.size == constructorFieldsNumber)

                // Now we repeatedly apply "replace" for each matched field
                // TODO: implement parallel replacement for better performance
                var result = branch.expr
                for ((i, field) in fields.withIndex()) {
                    result = shift(replace(result, shift(field, 1, 0), fields.size - 1 - i), -1, 0)
                }
                reduce(result, fullReduction)
            }

            is Expression.IfThenElse -> {
                TODO()
            }

            // In other cases, propagate recursion to tree branches without reduction on this
            // level
            is Expression.Lambda -> Expression.Lambda(e.name, e.annot, reduce(e.body, fullReduction))
            is Expression.Forall -> Expression.Forall(e.name, e.annot, reduce(e.body, fullReduction))
            is Expression.AppliedAtom -> Expression.AppliedAtom(e.atom,
                e.arguments.map { reduce(it, fullReduction) }.toTypedArray())
            is Expression.Var -> e
            is Expression.Builtin -> e
            is Expression.Sort -> e
        }
    }

    private fun reduceIfEnergetic(e: Expression, fullReduction: Boolean): Expression =
        if (fullReduction) reduce(e, fullReduction) else e


    // Auxiliary function at the core of beta and zeta reduction
    private fun shiftAndReplace(body: Expression, arg: Expression): Expression =
        shift(replace(body, shift(arg, 1, 0), 0), -1, 0)
}