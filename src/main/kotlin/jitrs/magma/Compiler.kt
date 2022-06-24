package jitrs.magma

import jitrs.links.CstGrammar
import jitrs.links.Grammar
import jitrs.links.parser.getContainingClassOrPackageName
import jitrs.magma.infer.Expression
import jitrs.magma.infer.Inference
import jitrs.magma.infer.PolyType

class Compiler private constructor(
    private val grammar: CstGrammar,
    private val inference: Inference
) {
    fun getIr(string: String): Expression {
        val cst = this.grammar.parse(string)
        return cstToIr(cst as Expr)
    }

    fun infer(expr: Expression): PolyType {
        return inference.infer(expr)
    }

    companion object {
        fun new(): Compiler {
            val containerName = getContainingClassOrPackageName(Expr::class.java)

            val grammar = Grammar.new(
                arrayOf("fun", "=>", "<ident>", "<int>", "(", ")", "let", "=", "in", "if", "then", "else", "<eof>"),
                arrayOf("Goal", "Expr", "Value"),
                rules(),
                { Character.isJavaIdentifierStart(it) },
                { Character.isJavaIdentifierPart(it) }
            )

            val cstGrammar = CstGrammar.new(grammar, containerName)

            return Compiler(cstGrammar, Inference.new())
        }
    }
}