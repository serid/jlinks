package jitrs.links.parser

import jitrs.links.Cst
import jitrs.links.tablegen.*
import jitrs.links.tokenizer.Scheme
import jitrs.links.tokenizer.SyntaxErrorException
import jitrs.links.tokenizer.Token
import jitrs.util.ArrayIterator
import jitrs.util.myAssert

/**
 * @param returnFirstParse GLR parser is capable of handling ambiguous grammars and returning multiple parse trees.
 * If `returnFirstParse` is `true`, parsing ends after finding first successful parse tree.
 */
fun parse(
    scheme: Scheme,
    table: Table,
    rules: Rules,
    tokens: ArrayIterator<Token>,
    returnFirstParse: Boolean,
    debug: Boolean
): Array<Cst> {
    val results = ArrayList<Cst>()
    val errors = ArrayList<SyntaxErrorException>()

    val processes: ArrayList<PProcess> = arrayListOf(
        // Initial process
        PProcess(scheme, table, rules, tokens, debug)
    )

    val newProcesses = ArrayList<PProcess>()

    outer@ while (processes.isNotEmpty()) {
        val iterator = processes.iterator()
        for (process in iterator)
            when (val isDone = process.step(newProcesses)) {
                is StepResult.Pending -> {
                }
                is StepResult.Error -> {
                    iterator.remove()
                    errors.add(isDone.error)
                }
                is StepResult.Done -> {
                    results.add(isDone.result)
                    if (returnFirstParse)
                        break@outer
                }
            }

        processes.addAll(newProcesses)
        newProcesses.clear()
    }

    if (results.isEmpty() && errors.isNotEmpty()) {
        val err = errors.joinToString("\n")
        throw RuntimeException("Syntax errors:\n$err")
    }

    return results.toTypedArray()
}

fun parseOne(scheme: Scheme, table: Table, rules: Rules, tokens: ArrayIterator<Token>, debug: Boolean): Cst {
    return parse(scheme, table, rules, tokens, returnFirstParse = true, debug = debug)[0]
}

/**
 * State of a parsing process
 */
class PProcess private constructor(
    private val scheme: Scheme, // immutable
    private val table: Table, // immutable
    private val rules: Rules, // immutable
    private val tokens: ArrayIterator<Token>,
    private val debug: Boolean, // immutable

    private var lookahead: Token,
    private val cstStack: ArrayList<Cst>,
    private val stateStack: ArrayList<StateId>,
) {
    constructor(
        scheme: Scheme,
        table: Table,
        rules: Rules,
        tokens: ArrayIterator<Token>,
        debug: Boolean,
    ) : this(
        scheme,
        table,
        rules,
        tokens,
        debug,
        tokens.next(),
        arrayListOf(),
        arrayListOf(0)
    )

    /**
     * @return concrete syntax tree or `null` if more steps are needed
     */
    fun step(newProcesses: ArrayList<PProcess>): StepResult =
        when (val action = this.table.map[this.stateStack.last()].action[this.lookahead.id]) {
            is Action.Just -> {
                this.invokeStackAction(action.action)
                StepResult.Pending
            }
            is Action.Fork -> {
                // Skip first action, fork a process for each remaining action and invoke the action there
                for (i in 1 until action.actions.size) {
                    val newPProcess = this.fork()
                    newPProcess.invokeStackAction(action.actions[i])
                    newProcesses.add(newPProcess)
                }

                // Invoke first stack action in this process
                this.invokeStackAction(action.actions[0])

                StepResult.Pending
            }
            is Action.Error -> {
                val actual = this.scheme.map.terminals[this.lookahead.id]
                val expected = this.table.map[this.stateStack.last()].action.asSequence()
                    .withIndex()
                    .filter { (_, action) -> action !is Action.Error }
                    .map { (id, _) -> "`${this.scheme.map.terminals[id]}`" }
                    .joinToString(", ")

                StepResult.Error(SyntaxErrorException("Expected one of $expected, found $actual", this.lookahead.span))
            }
            is Action.Done -> {
                this.stateStack.removeLast()

                if (debug) {
                    myAssert(stateStack.size == 1)
                    myAssert(stateStack.last() == 0)
                    myAssert(cstStack.size == 1)
                }
                StepResult.Done(this.cstStack.last())
            }
        }

    private fun invokeStackAction(stackAction: StackAction) {
        when (stackAction) {
            is StackAction.Shift -> {
                this.cstStack.add(Cst.Leaf(this.lookahead))
                this.stateStack.add(stackAction.state)
                this.lookahead = this.tokens.next()
                StepResult.Pending
            }
            is StackAction.Reduce -> {
                val rule = rules[stackAction.id]
                val lhsId = rule.lhs
                val rhsLength = rule.rhs.size

                // remove nodes from stack
                val poppedNodes0 = Array<Cst?>(rhsLength) { null }
                for (i in 0 until rhsLength) {
                    poppedNodes0[rhsLength - i - 1] = this.cstStack.removeLast()
                    this.stateStack.removeLast() // also remove states
                }
                val poppedCsts = poppedNodes0.requireNoNulls()

                // check that nodes from the stack match items expected by the rule
                if (debug) {
                    rule.rhs.asSequence()
                        .zip(poppedCsts.asSequence())
                        .all { (expected, actual) -> expected.compareWithNode(actual) }
                }

                // find next state using GOTO
                val oldState = this.stateStack.last()
                val nextState = this.table.map[oldState].goto[lhsId]

                this.cstStack.add(Cst.Node(lhsId, stackAction.id, poppedCsts))
                this.stateStack.add(nextState)
                StepResult.Pending
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fork(): PProcess = PProcess(
        scheme,
        table,
        rules,
        tokens.clone(),
        debug,
        lookahead,
        cstStack.clone() as ArrayList<Cst>,
        stateStack.clone() as ArrayList<StateId>
    )
}

sealed class StepResult {
    object Pending : StepResult()
    data class Error(val error: SyntaxErrorException) : StepResult()
    data class Done(val result: Cst) : StepResult()
}