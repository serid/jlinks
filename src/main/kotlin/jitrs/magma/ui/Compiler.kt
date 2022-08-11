package jitrs.magma.ui

import jitrs.links.Grammar
import jitrs.links.tokenizer.SyntaxErrorException
import jitrs.magma.infer.*
import jitrs.magma.ir.globals
import jitrs.magma.ir.stdlib
import jitrs.magma.syntax.*
import jitrs.util.exceptionPrintMessageAndTrace
import jitrs.util.myAssert
import kotlin.system.exitProcess

fun repl() {
    val compiler = try {
        ModuleCompiler()
    } catch (e: SyntaxErrorException) {
        exceptionPrintMessageAndTrace(e)
        exitProcess(1)
    }

    while (true) {
        print(">>> ")
        val line = readLine() ?: break
        if (line == "") continue

        if (line.startsWith(":eval")) try {
            val newLine = line.substring(5)

            val expressionCompiler = ExpressionCompiler(compiler.getModule(stdlib()).globals)
            val type = expressionCompiler.infer(newLine)
            val value = expressionCompiler.eval(newLine)

            println("$value : $type")
        } catch (e: SyntaxErrorException) {
            exceptionPrintMessageAndTrace(e)
        }
        else TODO()
    }
}

class ModuleCompiler {
    private val grammar: Grammar = moduleGrammar.value

    fun getModule(moduleText: String): FModule {
        val cst = this.grammar.parseOneCst(moduleText)
        return GlobalScopeToModule().entry(cst as GlobalScope)
    }
}

class ExpressionCompiler(
    private val globals: Globals = globals(), private val exVars: ExVariables = ExVariables()
) {
    private val grammar: Grammar = expressionGrammar.value

    fun infer(text: String): Type {
        val expr = getIr(text)
        return Inference.new(globals, exVars).infer(expr).first
    }

    fun eval(text: String): Type {
        val expr = getIr(text)
        Inference.new(globals, exVars).infer(expr)
        return Reductor(globals, exVars).reduce(expr, true)
    }

    fun getIr(string: String): Expression {
        val cst = this.grammar.parseOneCst(string)
        return ExprToIr(globals, exVars).entry(cst as Expr)
    }

    fun getIrNoExistentials(string: String): Expression {
        val exVars = ExVariables()
        val expr = this.getIr(string)
        myAssert(exVars.isEmpty())
        return expr
    }
}