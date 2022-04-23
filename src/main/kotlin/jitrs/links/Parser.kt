package jitrs.links

import jitrs.links.util.ArrayIterator
import jitrs.links.util.myAssert

fun parse(table: Table, rules: Rules, tokens: ArrayIterator<Token>, debug: Boolean = true): Cst {
    val cstStack = ArrayList<Cst>()
    val stateStack = ArrayList<StateId>()

    stateStack.add(0)

    var lookahead = tokens.next()

    while (true) {
        when (val action = table.map[stateStack.last()].action[lookahead.id]) {
            is Action.Shift -> {
                cstStack.add(Cst.Leaf(lookahead))
                stateStack.add(action.state)
                lookahead = tokens.next()
            }
            is Action.Reduce -> {
                val rule = rules[action.id]
                val lhsId = rule.lhs
                val rhsLength = rule.rhs.size

                // remove nodes from stack
                val poppedNodes0 = Array<Cst?>(rhsLength) { null }
                for (i in 0 until rhsLength) {
                    poppedNodes0[rhsLength - i - 1] = cstStack.removeLast()
                    stateStack.removeLast() // also remove states
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
                val oldState = stateStack.last()
                val nextState = table.map[oldState].goto[lhsId]

                cstStack.add(Cst.Node(lhsId, poppedCsts))
                stateStack.add(nextState)
            }
            is Action.Error -> throw RuntimeException()
            is Action.Done -> {
                stateStack.removeLast()
                break
            }
        }
    }

    myAssert(debug, stateStack.size == 1)
    myAssert(debug, stateStack.last() == 0)
    myAssert(debug, cstStack.size == 1)
    return cstStack.last()
}