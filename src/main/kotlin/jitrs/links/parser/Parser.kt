package jitrs.links.parser

import jitrs.links.Cst
import jitrs.links.tablegen.*
import jitrs.links.tokenizer.Scheme
import jitrs.links.tokenizer.SyntaxErrorException
import jitrs.links.tokenizer.Token
import jitrs.util.ArrayIterator
import jitrs.util.SimpleTallException
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
    source: String,
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
    val newErrors = ArrayList<SyntaxErrorException>()

    outer@ while (processes.isNotEmpty()) {
        val iterator = processes.iterator()
        for (process in iterator)
            when (val isDone = process.step(newProcesses, newErrors)) {
                is StepResult.Pending -> {
                }
                is StepResult.Error -> {
                    iterator.remove()
                }
                is StepResult.Done -> {
                    results.add(isDone.result)
                    iterator.remove()
                    if (returnFirstParse)
                        break@outer
                }
            }

        processes.addAll(newProcesses)
        newProcesses.clear()

        errors.addAll(newErrors)
        newErrors.clear()
    }

    if (results.isEmpty() && errors.isNotEmpty()) {
        for (error in errors) error.setSource(source)
        val err = errors.joinToString("\n") { it.message }
        throw SimpleTallException("Syntax errors", "Syntax errors:\n$err")
    }

    return results.toTypedArray()
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
    fun step(newProcesses: ArrayList<PProcess>, errors: ArrayList<SyntaxErrorException>): StepResult =
        when (val action = this.table.map[this.stateStack.last()]
            .action[this.lookahead.id]) {
            is Action.Just -> {
                val result = this.invokeStackAction(action.action)
                upcastSyntaxError(result, errors)
            }
            is Action.Fork -> {
                // Skip first action, fork a process for each remaining action and invoke the action there
                for (i in 1 until action.actions.size) {
                    val newPProcess = this.fork()

                    val result = newPProcess.invokeStackAction(action.actions[i])
                    if (result == null)
                        newProcesses.add(newPProcess)
                    else
                        errors.add(result)
                }

                // Invoke first stack action in this process
                val result = this.invokeStackAction(action.actions[0])
                upcastSyntaxError(result, errors)
            }
            is Action.Error -> {
                val actual = this.scheme.map.terminals[this.lookahead.id]
                val expected = this.table.map[this.stateStack.last()].action.asSequence()
                    .withIndex()
                    .filter { (_, action) -> action !is Action.Error }
                    .map { (id, _) -> "`${this.scheme.map.terminals[id]}`" }
                    .joinToString(", ")

                errors.add(
                    SyntaxErrorException(
                        "Expected one of $expected, found $actual",
                        span = this.lookahead.span
                    )
                )
                StepResult.Error
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

    private fun invokeStackAction(stackAction: StackAction): SyntaxErrorException? {
        when (stackAction) {
            is StackAction.Shift -> {
                this.cstStack.add(Cst.Leaf(this.lookahead))
                this.stateStack.add(stackAction.state)
                this.lookahead = this.tokens.next()
                return null
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

                if (nextState == -1) {
                    val actual = this.scheme.map.nonTerminals[lhsId]
                    val expected = this.table.map[oldState].goto.asSequence()
                        .withIndex()
                        .filter { (_, action) -> action != -1 }
                        .map { (id, _) -> "`${this.scheme.map.nonTerminals[id]}`" }
                        .joinToString(", ")

                    return SyntaxErrorException(
                        "Expected one of $expected, found $actual",
                        span = this.lookahead.span
                    )
                } else {
                    this.cstStack.add(Cst.Node(lhsId, stackAction.id, poppedCsts))
                    this.stateStack.add(nextState)

                    return null
                }
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

private fun upcastSyntaxError(error: SyntaxErrorException?, errors: ArrayList<SyntaxErrorException>): StepResult =
    if (error == null)
        StepResult.Pending
    else {
        errors.add(error)
        StepResult.Error
    }

sealed class StepResult {
    object Pending : StepResult()
    object Error : StepResult()
    data class Done(val result: Cst) : StepResult()
}