package jitrs.magma.syntax

import jitrs.datastructures.PersistentList
import jitrs.magma.infer.*
import jitrs.magma.ir.Builtins
import jitrs.magma.ir.globalsPrelude
import jitrs.util.LateInit

class GlobalScopeToModule {
    private val module: FModule = globalsPrelude()
    private val topLevelTranslator: TopLevelTranslator = TopLevelTranslator(module)

    fun entry(globalScope: GlobalScope): FModule {
        visitGlobalScope(globalScope)
        return module
    }

    private fun visitGlobalScope(globalScope: GlobalScope) {
        var x = globalScope
        while (true) when (x) {
            is GlobalScope.Cons -> {
                topLevelTranslator.visitToplevel(x.toplevel)
                x = x.globalScope
            }

            is GlobalScope.Final -> {
                topLevelTranslator.visitToplevel(x.toplevel)
                break
            }
        }
    }
}

class TopLevelTranslator(
    private val module: FModule
) {
    fun visitToplevel(toplevel: Toplevel): Unit = when (toplevel) {
        is Toplevel.Definition -> {
            val exVars = ExVariables()
            val body = ExprToIr(module.globals, exVars).entry(toplevel.body)
            val (type, _) = Inference.new(module.globals, exVars).infer(body)
            this.module.globals[toplevel.name] = GlobalDefinition(body, type)
        }

        is Toplevel.Inductive -> {
            // Compile annot
            val exVars = ExVariables()
            val annot = ExprToIr(module.globals, exVars).entry(toplevel.annot)
            Inference.new(module.globals, exVars).assertTypeOfExpressionIsSort(annot)

            // Add the inductive to global map
            val constructorsLateInit = LateInit<Array<Constructor>>()
            val inductive = Inductive(toplevel.name, annot, constructorsLateInit)
            this.module.globals[toplevel.name] = GlobalDefinition(
                Expression.AppliedAtom.ofInd(inductive), Expression.Sort
            )

            // Visit constructors and fill-in lateinit
            val constructors = visitInductiveBranch(toplevel.inductiveBranch)
            constructorsLateInit.resolve(constructors)

            for ((i, c) in constructors.withIndex()) this.module.globals[c.name] = GlobalDefinition(
                Expression.AppliedAtom.ofConstru(inductive, i), c.type
            )
        }
    }

    private fun visitInductiveBranch(inductiveBranch: InductiveBranch): Array<Constructor> {
        val constructors = arrayListOf<Constructor>()

        var x = inductiveBranch
        while (true) when (x) {
            is InductiveBranch.Cons -> {
                val exVars = ExVariables()
                val type = ExprToIr(module.globals, exVars).entry(x.type)
                Inference.new(module.globals, exVars).assertTypeOfExpressionIsSort(type)
                constructors.add(Constructor(x.name, type))
                x = x.inductiveBranch
            }

            is InductiveBranch.Nil -> break
        }

        return constructors.toTypedArray()
    }
}

class ExprToIr(
    private val globals: Globals,
    private val exVars: ExVariables
) {

    fun entry(cst: Expr): Expression {
        return exprToIr(cst, PersistentList.Nil())
    }

    private fun exprToIr(expr: Expr, bindings: Bindings): Expression = when (expr) {
        is Expr.Just -> additiveToIr(expr.value, bindings)
    }

    // Binary operators are encoded as "int_plus (pair int int x y)"

    private fun additiveToIr(additive: Additive, bindings: Bindings): Expression = when (additive) {
        is Additive.Addition -> Expression.Application(
            Expression.Global("int_plus"),
            mkApplicationTree(
                Builtins.pairC,
                Expression.Builtin(Builtins.FInt),
                Expression.Builtin(Builtins.FInt),
                multiplicativeToIr(additive.e1, bindings),
                additiveToIr(additive.e2, bindings)
            )
        )
        is Additive.Mul -> multiplicativeToIr(additive.value, bindings)
    }

    private fun multiplicativeToIr(multiplicative: Multiplicative, bindings: Bindings): Expression = when (multiplicative) {
        is Multiplicative.Multiplication -> Expression.Application(
            Expression.Global("int_mul"),
            mkApplicationTree(
                Builtins.pairC,
                Expression.Builtin(Builtins.FInt),
                Expression.Builtin(Builtins.FInt),
                applyToIr(multiplicative.e1, bindings),
                multiplicativeToIr(multiplicative.e2, bindings)
            )
        )
        is Multiplicative.Apl -> applyToIr(multiplicative.value, bindings)
    }

    private fun applyToIr(apply: Apply, bindings: Bindings): Expression = when (apply) {
        is Apply.Application -> Expression.Application(exprToIr(apply.func, bindings), valueToIr(apply.arg, bindings))
        is Apply.Just -> valueToIr(apply.value, bindings)
    }

    private fun valueToIr(value: Val, bindings: Bindings): Expression = when (value) {
        is Val.Lambda -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.Lambda(value.name, allocExistentialVariable(exVars), exprToIr(value.body, newBindings))
        }

        is Val.LambdaAnnot -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.Lambda(value.name, exprToIr(value.annot, bindings), exprToIr(value.body, newBindings))
        }

        is Val.LetIn -> {
            val newBindings = PersistentList.Cons(value.name, bindings)
            Expression.LetIn(value.name, exprToIr(value.func, bindings), exprToIr(value.body, newBindings))
        }

        is Val.IfThenElse -> {
            Expression.IfThenElse(
                exprToIr(value.cond, bindings), exprToIr(value.aye, bindings), exprToIr(value.nay, bindings)
            )
        }

        is Val.Parens -> exprToIr(value.parenthesized, bindings)
        is Val.Num -> Expression.Builtin(Builtins.IntConst(value.num))
        is Val.ExVar -> allocExistentialVariable(exVars)
        is Val.Match -> {
            val (inductive, branches) = visitMatchBranch(value.matchBranch, bindings)
            Expression.Match(inductive, exprToIr(value.expr, bindings), branches)
        }

        is Val.Var -> {
            // Try to find a variable with this name in locals, then in globals
            // TODO: module system

            val variableNumber = bindings.asSequence().indexOf(value.name)
            if (variableNumber != -1) {
                // De Bruijn indexes are 0-based
                Expression.Var(variableNumber)
            } else {
                // Refer to globals
                if (!globals.containsKey(value.name))
                    throw RuntimeException("${value.name} item not found")
                Expression.Global(value.name)
            }
        }

        is Val.Arrow -> processForall("<arrow>", value.type1, value.type2, bindings)
        is Val.Forall -> processForall(value.name, value.annot, value.type, bindings)

        is Val.Sort -> Expression.Sort
    }

    private fun processForall(name: String, annot: Expr, type: Expr, bindings: Bindings): Expression {
        val newBindings = PersistentList.Cons(name, bindings)
        return Expression.Forall(name, exprToIr(annot, bindings), exprToIr(type, newBindings))
    }

    private fun visitMatchBranch(
        matchBranch: MatchBranch, bindings: Bindings
    ): Pair<Inductive, Array<Expression.Match.Branch>> {
        var inductive: Inductive? = null

        val constructors = arrayListOf<Expression.Match.Branch>()

        var x = matchBranch
        while (true) when (x) {
            is MatchBranch.Cons -> {
                // Find the constructor in the "globals" map
                val constru = globals[x.constructor] ?: throw RuntimeException("Constructor not found")
                val constructor = (constru.value as Expression.AppliedAtom).atom as Expression.AppliedAtom.Atom.Constru
                val id = constructor.id

                // Retrieve inductive from the constructor
                if (inductive == null) inductive = constructor.inductive
                else if (inductive != constructor.inductive) throw RuntimeException("Constructors from different inductives")

                // Add binders to "bindings"
                val newBindings = appendBinders(bindings, visitMatchBinder(x.matchBinder))

                val body = exprToIr(x.expr, newBindings)
                constructors.add(Expression.Match.Branch(id, body))
                x = x.matchBranch
            }

            is MatchBranch.Nil -> break
        }

        return Pair(inductive!!, constructors.toTypedArray())
    }

    private fun visitMatchBinder(matchBinder: MatchBinder): Array<String> {
        val names = arrayListOf<String>()

        var x = matchBinder
        while (true) when (x) {
            is MatchBinder.Cons -> {
                names.add(x.binder)
                x = x.matchBinder
            }

            is MatchBinder.Nil -> break
        }

        return names.toTypedArray()
    }
}

typealias Bindings = PersistentList<String>
//typealias Bindings = PersistentListWithLength<String>

data class FModule(
    // [globals] are only available inside the module and include imported items,
    // while [exports] are what outside modules see.
    val globals: Globals,
    val exports: Globals,
)
typealias Globals = HashMap<KernelName, GlobalDefinition>

/**
 * Auxiliary function to append binders to an existing bindings list.
 * Bindings are usually listed in head-right order, that's the convention used for [bindings]
 * and the return value. [binders], however is interpreted from left to right.
 */
fun <T> appendBinders(bindings: PersistentList<T>, binders: Array<T>): PersistentList<T> {
    val reversedBinders = binders.reversed().asSequence()
    return PersistentList.fromSequence(reversedBinders).append(bindings)
}