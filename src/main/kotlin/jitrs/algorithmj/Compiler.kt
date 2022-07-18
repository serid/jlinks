package jitrs.algorithmj

import jitrs.algorithmj.infer.Expression
import jitrs.algorithmj.infer.Inference
import jitrs.algorithmj.infer.PolyType
import jitrs.algorithmj.syntax.Expr
import jitrs.algorithmj.syntax.cstToIr
import jitrs.algorithmj.syntax.grammar
import jitrs.links.Grammar

class Compiler private constructor(
    private val grammar: Grammar,
    private val inference: Inference
) {
    fun getIr(string: String): Expression {
        val cst = this.grammar.parseOneCst(string)
        return cstToIr(cst as Expr)
    }

    fun infer(expr: Expression): PolyType {
        return inference.infer(expr)
    }

    companion object {
        fun new(): Compiler {
            return Compiler(grammar(), Inference.new())
        }
    }
}