package jitrs.magma.ir

import jitrs.magma.infer.Expression
import jitrs.magma.infer.GlobalDefinition
import jitrs.magma.syntax.FModule
import jitrs.magma.syntax.Globals

fun globals(): Globals {
    return hashMapOf(
        "int" to GlobalDefinition(Expression.Builtin(Builtins.FInt), Expression.Sort),
        "int_plus" to GlobalDefinition(Expression.Builtin(Builtins.intPlus), Builtins.intPlus.type()),
        "int_mul" to GlobalDefinition(Expression.Builtin(Builtins.intMul), Builtins.intMul.type()),
        "int_eq" to GlobalDefinition(Expression.Builtin(Builtins.intEq), Builtins.intEq.type()),
    )
}

fun globalsPrelude(): FModule {
    return FModule(globals(), globals())
}

fun stdlib() = """
            -- Langitems
            inductive Bool : Sort | true : Bool | false : Bool end
            inductive Pair : Sort -> Sort -> Sort
            | pair : forall A : Sort, forall B : Sort, A -> B -> Pair A B
            end
            """.trimIndent() + "\n"