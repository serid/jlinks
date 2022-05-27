package jitrs.links

import jitrs.links.util.ArrayIterator
import jitrs.links.util.myAssert

fun parse(table: Table, rules: Rules, tokens: ArrayIterator<Token>, debug: Boolean = true): Cst {
    val processes: ArrayList<PProcess> = arrayListOf(
        // Initial process
        PProcess(table, rules, tokens, debug)
    )

    outer@ while (true) {
        val iterator = processes.iterator()
        for (process in iterator) {
            val isDone = process.step()

            when (isDone) {
                is StepResult.Pending -> {}
                is StepResult.Error -> iterator.remove()
                is StepResult.Done -> return isDone.result
            }
        }
    }
}

/**
 * State of a parsing process
 */
class PProcess private constructor(
    private val table: Table, // immutable
    private val rules: Rules, // immutable
    private val tokens: ArrayIterator<Token>,
    private val debug: Boolean, // immutable

    private var lookahead: Token,
    private val cstStack: ArrayList<Cst>,
    private val stateStack: ArrayList<StateId>,
) {
    constructor(
        table: Table,
        rules: Rules,
        tokens: ArrayIterator<Token>,
        debug: Boolean,
    ) : this(
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
    fun step(): StepResult =
        when (val action = this.table.map[this.stateStack.last()].action[this.lookahead.id]) {
            is Action.Shift -> {
                this.cstStack.add(Cst.Leaf(this.lookahead))
                this.stateStack.add(action.state)
                this.lookahead = this.tokens.next()
                StepResult.Pending
            }
            is Action.Reduce -> {
                val rule = rules[action.id]
                val lhsId = rule.lhs
                val rhsLength = rule.rhs.size

                // remove nodes from stack
                val poppedNodes0 = Array<Cst?>(rhsLength) { null }
                for (i in 0 until rhsLength) {
                    poppedNodes0[rhsLength - i - 1] = this.cstStack.removeLast()
                    this.stateStack.removeLast() // also remove states
                }
                // none of elements are null
                // source: trust me bro
                @Suppress("UNCHECKED_CAST")
                val poppedCsts = poppedNodes0 as Array<Cst>

                // check that nodes from the stack match items expected by the rule
                if (debug) {
                    rule.rhs.asSequence()
                        .zip(poppedCsts.asSequence())
                        .all { (expected, actual) -> expected.compareWithNode(actual) }
                }

                // find next state using GOTO
                val oldState = this.stateStack.last()
                val nextState = this.table.map[oldState].goto[lhsId]

                this.cstStack.add(Cst.Node(lhsId, poppedCsts))
                this.stateStack.add(nextState)
                StepResult.Pending
            }
            is Action.Error -> throw RuntimeException()
            is Action.Done -> {
                this.stateStack.removeLast()

                myAssert(debug, stateStack.size == 1)
                myAssert(debug, stateStack.last() == 0)
                myAssert(debug, cstStack.size == 1)
                StepResult.Done(this.cstStack.last())
            }
        }

    private fun fork(): PProcess = PProcess(
        table,
        rules,
        tokens.clone(),
        debug,
        lookahead,
        cstStack,
        stateStack
    )
}

sealed class StepResult {
    object Pending : StepResult()
    object Error : StepResult()
    data class Done(val result: Cst) : StepResult()
}