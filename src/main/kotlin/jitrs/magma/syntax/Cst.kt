@file:Suppress("CanSealedSubClassBeObject")

package jitrs.magma.syntax

import jitrs.links.Grammar
import jitrs.links.parser.AutoCst
import jitrs.links.parser.getContainingClassOrPackageName

// Cst, has strings for variable names
sealed class GoalExpression : AutoCst() {
    data class Goal1(val expr: Expr) : GoalExpression()
}

sealed class Expr : AutoCst() {
    data class Just(val value: Additive) : Expr()
}

sealed class Additive : AutoCst() {
    data class Addition(val e1: Multiplicative, val e2: Additive) : Additive()

    data class Mul(val value: Multiplicative) : Additive()
}

sealed class Multiplicative : AutoCst() {
    data class Multiplication(val e1: Apply, val e2: Multiplicative) : Multiplicative()

    data class Apl(val value: Apply) : Multiplicative()
}

sealed class Apply : AutoCst() {
    data class Application(val func: Expr, val arg: Val) : Apply()

    data class Just(val value: Val) : Apply()
}

sealed class Val : AutoCst() {
    data class Lambda(val name: String, val body: Expr) : Val()

    data class LambdaAnnot(val name: String, val annot: Expr, val body: Expr) : Val()

    data class LetIn(val name: String, val func: Expr, val body: Expr) : Val()

    data class IfThenElse(val cond: Expr, val aye: Expr, val nay: Expr) : Val()

    data class Parens(val parenthesized: Expr) : Val()

    data class Num(val num: Int) : Val()

    /**
     * An existential variable
     */
    class ExVar : Val()

    data class Match(val expr: Expr, val matchBranch: MatchBranch) : Val()

    data class Var(val name: String) : Val()


    // Types

    data class Arrow(val type1: Expr, val type2: Expr) : Val()

    data class Forall(val name: String, val annot: Expr, val type: Expr) : Val()

    class Sort : Val()
}

sealed class MatchBranch : AutoCst() {
    data class Cons(
        val constructor: String, val matchBinder: MatchBinder, val expr: Expr, val matchBranch: MatchBranch
    ) : MatchBranch()

    class Nil : MatchBranch()
}

sealed class MatchBinder : AutoCst() {
    data class Cons(
        val binder: String, val matchBinder: MatchBinder
    ) : MatchBinder()

    class Nil : MatchBinder()
}

sealed class GoalModule : AutoCst() {
    data class Goal1(val globalScope: GlobalScope) : GoalModule()
}

sealed class GlobalScope : AutoCst() {
    data class Cons(val toplevel: Toplevel, val globalScope: GlobalScope) : GlobalScope()

    data class Final(val toplevel: Toplevel) : GlobalScope()
}

sealed class Toplevel : AutoCst() {
    data class Definition(val name: String, val body: Expr) : Toplevel()

    data class Inductive(val name: String, val annot: Expr, val inductiveBranch: InductiveBranch) : Toplevel()
}

sealed class InductiveBranch : AutoCst() {
    data class Cons(
        val name: String, val type: Expr, val inductiveBranch: InductiveBranch
    ) : InductiveBranch()

    class Nil : InductiveBranch()
}


// Derive two rule sets for parsing modules and expressions

val moduleGrammar: Lazy<Grammar> = lazy { produceGrammar(moduleTerminals(), moduleNonTerminals(), moduleRules()) }

val expressionGrammar: Lazy<Grammar> = lazy { produceGrammar(expressionTerminals(), expressionNonTerminals(), expressionRules()) }

fun produceGrammar(terminals: Array<String>, nonTerminals: Array<String>, rules: String): Grammar {
    val containerName = getContainingClassOrPackageName(Expr::class.java)

    return Grammar.new(terminals,
        nonTerminals,
        rules,
        containerName,
        { Character.isJavaIdentifierStart(it) },
        { Character.isJavaIdentifierPart(it) })
}

fun moduleTerminals() = arrayOf("def", "inductive") + expressionTerminals()

fun expressionTerminals() = arrayOf(
    "fun",
    "=>",
    "->",
    "forall",
    ":",
    ",",
    "<ident>",
    "<int>",
    "(",
    ")",
    "let",
    "=",
    "in",
    "if",
    "then",
    "else",
    "match",
    "|",
    "end",
    "Sort",
    "_",
    "+",
    "*",
    "<eof>"
)

fun moduleRules() = """
            GoalModule.Goal1 -> GlobalScope <eof>
    
            -- Global definitions
            GlobalScope.Cons -> Toplevel GlobalScope
            GlobalScope.Final -> Toplevel
            
            Toplevel.Definition -> def <ident> = Expr
            Toplevel.Inductive -> inductive <ident> : Expr InductiveBranch
            InductiveBranch.Cons -> | <ident> : Expr InductiveBranch
            InductiveBranch.Nil -> end
            """ + baseExpressionRules()

fun expressionRules() = "GoalExpression.Goal1 -> Expr <eof>\n" + baseExpressionRules()

fun baseExpressionRules() = """
            -- Insert Goal rule before this string
            
            Expr.Just -> Additive
            
            Additive.Addition -> Multiplicative + Additive
            Additive.Mul -> Multiplicative
            Multiplicative.Multiplication -> Apply * Multiplicative
            Multiplicative.Apl -> Apply
            
            Apply.Application -> Expr Val
            Apply.Just -> Val
            
            Val.Lambda -> fun <ident> => Expr
            Val.LambdaAnnot -> fun <ident> : Expr => Expr
            Val.LetIn -> let <ident> = Expr in Expr
            Val.IfThenElse -> if Expr then Expr else Expr
            Val.Parens -> ( Expr )
            Val.Num -> <int>
            Val.Var -> <ident> -- Can reference a local variable or a global definition
            Val.ExVar -> _
            
            Val.Match -> match Expr MatchBranch
            MatchBranch.Cons -> | <ident> MatchBinder Expr MatchBranch
            MatchBranch.Nil -> end
            MatchBinder.Cons -> <ident> MatchBinder
            MatchBinder.Nil -> "->"
            
            -- Types
            Val.Arrow -> Expr "->" Expr
            Val.Forall -> forall <ident> : Expr , Expr
            Val.Sort -> Sort
"""

fun moduleNonTerminals() =
    arrayOf("GoalModule") + arrayOf("GlobalScope", "Toplevel", "InductiveBranch") + baseExpressionNonTerminals()

fun expressionNonTerminals() = arrayOf("GoalExpression") + baseExpressionNonTerminals()

fun baseExpressionNonTerminals() = arrayOf("Expr", "Additive", "Multiplicative", "Apply", "Val", "MatchBranch", "MatchBinder")