package jitrs.links.tablegen

import jitrs.links.tokenizer.Scheme

fun generateTable(scheme: Scheme, rules: Rules): Table {
    val follow = computeFollowMap(scheme, rules)

    val rows = ArrayList<Row>()

    // Collection of items to be processed
    val queue = ArrayDeque<MutableItemSet>()
    // Map rows from resulting table to item sets
    val visitedSets = ArrayList<ItemSet>()

    val initialSet = closeItemSet(mutableSetOf(Item(0, 0)), rules)
    queue.add(initialSet)

    while (queue.isNotEmpty()) {
        val sourceSet = queue.removeFirst()

        val reductionRuleIds = ArrayList<RuleId>()
        var endsInEof = false

        val reachableSets = scheme.map.newOf<MutableItemSet> { mutableSetOf() }

        for (item in sourceSet) {
            val nextSymbol = item.nextSymbol(rules)

            // skip eof token
            if (nextSymbol is Symbol.Terminal && nextSymbol.id == scheme.specialIdInfo.eofSpecialId) {
                endsInEof = true
                continue
            }

            // Select set depending on symbol type
            val set = when (nextSymbol) {
                null -> {
                    // Infer reduction actions and continue
                    reductionRuleIds.add(item.ruleId)

                    continue
                }
                is Symbol.Terminal -> reachableSets.terminals[nextSymbol.id]
                is Symbol.NonTerminal -> reachableSets.nonTerminals[nextSymbol.id]
            }
            // Add new item where dot is moved by one
            set.add(Item(item.index + 1, item.ruleId))
        }

        rows.add(
            computeRow(
                scheme = scheme,
                rules = rules,
                follow = follow,
                visitedSets = visitedSets,
                newStateId = rows.size,
                reachableSets = reachableSets,
                reductionRuleIds = reductionRuleIds,
                endsInEof = endsInEof,
                queue = queue,
            )
        )
        visitedSets.add(sourceSet)
    }

    return Table(rows.toTypedArray())
}

fun computeRow(
    // Invariant parameters
    scheme: Scheme,
    rules: Rules,
    follow: Follow,

    // Changes on every invocation
    visitedSets: ArrayList<ItemSet>,

    // Info specific to an item set
    newStateId: StateId,
    reachableSets: SymbolArray<MutableItemSet>,
    reductionRuleIds: ArrayList<RuleId>,
    endsInEof: Boolean,

    // Is mutated
    queue: ArrayDeque<MutableItemSet>,
): Row {
    // Loop that will be run for two arrays of reachable sets
    fun loop(setsReachable: Array<MutableItemSet>, payload: (id: Int, targetState: StateId) -> Unit) {
        for ((id, set0) in setsReachable.withIndex()) {
            if (set0.isEmpty()) continue
            val set = closeItemSet(set0, rules)

            val index = visitedSets.indexOf(set)
            val targetState = if (index == -1) {
                // If the set was not encountered before, enqueue it
                queue.add(set)
                newStateId + queue.size
            } else {
                index
            }

            payload(id, targetState)
        }
    }

    @Suppress("RemoveExplicitTypeArguments")
    val goto = Array<StateId>(scheme.map.nonTerminals.size) { -1 }
    val actions = Array<Action>(scheme.map.terminals.size) { Action.Error }

    loop(reachableSets.terminals) { id, targetState -> actions[id] = Action.Just(StackAction.Shift(targetState)) }
    loop(reachableSets.nonTerminals) { id, targetState -> goto[id] = targetState }

    // Place reduction actions
    for (id in actions.indices)
    // Use follow map to determine whether the reduction should be placed
        for (ruleId in reductionRuleIds)
            if (mustBeAdded(rules, follow, ruleId, id)) {
                val newStackAction = StackAction.Reduce(ruleId)
                when (val targetAction = actions[id]) {
                    is Action.Error -> actions[id] = Action.Just(newStackAction)
                    is Action.Just -> actions[id] = Action.Fork(arrayOf(targetAction.action, newStackAction))
                    is Action.Fork -> actions[id] = Action.Fork(targetAction.actions + newStackAction)
                    else -> throw Error("Done-reduce conflict")
                }
            }

    // Place action for eof
    if (endsInEof) actions[scheme.specialIdInfo.eofSpecialId] = Action.Done

    return Row(actions, goto)
}

/**
 * Compute closure of an ItemSet
 */
fun closeItemSet(itemSet: MutableItemSet, rules: Rules): MutableItemSet {
    // one set for iteration, one for adding new items
    var set1 = itemSet
    var set2 = mutableSetOf<Item>()
    set2.addAll(set1)

    val alreadyProcessed = mutableSetOf<Item>()
    while (true) {
        // Stop processing if no changes are introduced to the set
        var weDoneYet = true

        for (item in set1) {
            if (alreadyProcessed.contains(item)) continue
            weDoneYet = false

            // If the symbol after dot is a nonterminal, find all rules that produce it and add them to the set
            when (val nextSymbol = item.nextSymbol(rules)) {
                is Symbol.NonTerminal -> for (id in rules.indices) {
                    if (rules[id].lhs == nextSymbol.id) {
                        set2.add(Item(0, id))
                    }
                }
            }

            alreadyProcessed.add(item)
        }

        if (weDoneYet) return set2

        // switch sets
        val tmp = set1
        set1 = set2
        set2 = tmp
    }
}

typealias MutableItemSet = MutableSet<Item>
typealias ItemSet = Set<Item>

/**
 * Item used in the table generation algorithm
 * */
data class Item(
    val index: Int,
    val ruleId: RuleId,
) {
    // Return the symbol after dot if it exists
    fun nextSymbol(rules: Rules): Symbol? =
        if (index < rules[ruleId].rhs.size)
            rules[ruleId].rhs[index]
        else null
}