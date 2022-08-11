// Following functions implement beta reduction for lambda terms with de Bruijn indices
// https://www.cs.cornell.edu/courses/cs4110/2012fa/lectures/lecture14.pdf

package jitrs.magma.infer

/**
 * Replace an occurrence of a variable by its value.
 */
fun replace(e: Expression, value: Expression, m: Index): Expression =
    when (e) {
        is Expression.Var -> if (e.index == m) value else e

        // Shift "value" to account for new binders in Lambda, Forall, LetIn, Match
        is Expression.Lambda -> Expression.Lambda(
            e.name,
            replace(e.annot, value, m),
            replace(e.body, shift(value, 1, 0), m + 1)
        )
        is Expression.Forall -> Expression.Forall(
            e.name,
            replace(e.annot, value, m),
            replace(e.body, shift(value, 1, 0), m + 1)
        )
        is Expression.LetIn -> Expression.LetIn(
            e.name,
            replace(e.func, value, m),
            replace(e.body, shift(value, 1, 0), m + 1)
        )
        is Expression.Match -> Expression.Match(
            e.inductive,
            replace(e.scrutinee, value, m),
            e.branches.map {
                val numberOfFields = e.getConstructorFieldTypes(it.constructorId).size
                Expression.Match.Branch(
                    it.constructorId, replace(
                        it.expr,
                        shift(value, numberOfFields, 0),
                        m + numberOfFields
                    )
                )
            }.toTypedArray()
        )

        // In other cases, call recursively without modification on this level

        is Expression.AppliedAtom -> Expression.AppliedAtom(e.atom,
            e.arguments.map { replace(it, value, m) }.toTypedArray())
        is Expression.Application -> Expression.Application(replace(e.func, value, m), replace(e.arg, value, m))
        is Expression.IfThenElse -> Expression.IfThenElse(
            replace(e.cond, value, m),
            replace(e.aye, value, m),
            replace(e.nay, value, m)
        )
        is Expression.ExVar -> e
        is Expression.Global -> e
        is Expression.Builtin -> e
        is Expression.Sort -> e
    }

fun shift(e: Expression, i: Index, c: Index): Expression =
    when (e) {
        is Expression.Var -> if (e.index < c) e else Expression.Var(e.index + i)
        is Expression.Lambda -> Expression.Lambda(
            e.name,
            shift(e.annot, i, c),
            shift(e.body, i, c + 1)
        )
        is Expression.Forall -> Expression.Forall(
            e.name,
            shift(e.annot, i, c),
            shift(e.body, i, c + 1)
        )
        is Expression.LetIn -> Expression.LetIn(
            e.name,
            shift(e.func, i, c),
            shift(e.body, i, c + 1)
        )
        is Expression.Match -> Expression.Match(
            e.inductive,
            shift(e.scrutinee, i, c),
            e.branches.map {
                val numberOfFields = e.getConstructorFieldTypes(it.constructorId).size
                Expression.Match.Branch(
                    it.constructorId, shift(
                        it.expr,
                        i,
                        c + numberOfFields
                    )
                )
            }.toTypedArray()
        )

        // In other cases, call recursively without modification on this level

        is Expression.AppliedAtom -> Expression.AppliedAtom(e.atom,
            e.arguments.map { shift(it, i, c) }.toTypedArray())
        is Expression.Application -> Expression.Application(shift(e.func, i, c), shift(e.arg, i, c))
        is Expression.IfThenElse -> Expression.IfThenElse(
            shift(e.cond, i, c),
            shift(e.aye, i, c),
            shift(e.nay, i, c)
        )
        is Expression.ExVar -> e
        is Expression.Global -> e
        is Expression.Builtin -> e
        is Expression.Sort -> e
    }
