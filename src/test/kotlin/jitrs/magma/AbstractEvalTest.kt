package jitrs.magma

import jitrs.magma.infer.Expression
import jitrs.magma.ir.stdlib
import jitrs.magma.ui.ExpressionCompiler
import jitrs.magma.ui.ModuleCompiler
import jitrs.util.EqualityWrapper
import org.junit.jupiter.api.Assertions.assertEquals

internal abstract class AbstractEvalTest {
    private val moduleCompiler = ModuleCompiler()

    protected fun test(moduleText: String, expr1: String, expr2: String) {
        val text = stdlib() + moduleText
        val mod = moduleCompiler.getModule(text)
        val e1 = ExpressionCompiler(mod.globals).eval(expr1)
        val e2 = ExpressionCompiler(mod.globals).eval(expr2)
        assertEquals(EqualityWrapper(e1, Expression::deepEquality), EqualityWrapper(e2, Expression::deepEquality))
    }
}