package jitrs.magma

import jitrs.links.Grammar
import jitrs.magma.infer.Expression
import jitrs.magma.infer.Inference
import jitrs.magma.infer.PolyType

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