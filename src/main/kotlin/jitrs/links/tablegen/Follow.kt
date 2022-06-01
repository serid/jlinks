package jitrs.links.tablegen

import jitrs.links.tokenizer.Scheme

// For each symbol, follow set of right-most terminals are left-most terminals of the following symbol.

// Entry point
fun computeFollowMap(scheme: Scheme, rules: Rules): Follow {
    val initials = computeInitials(scheme, rules)
    val finals = computeFinals(scheme, rules)

    val follows = scheme.map.newOf { mutableSetOf<TerminalId>() }

    for (rule in rules) {
        // Iterate over symbols without last one
        for (i in 0 until (rule.rhs.size - 1)) {
            // Match symbol for which follow map is computed
            val fromSymbolsFinals = when (val fromSymbol = rule.rhs[i]) {
                is Symbol.Terminal -> sequenceOf(Symbol.Terminal(fromSymbol.id))
                is Symbol.NonTerminal -> finals[fromSymbol.id].asSequence()
            }
            // Select a set for each symbol in fromSymbol's finals
            val setsToExtend = fromSymbolsFinals.map {
                when (it) {
                    is Symbol.Terminal -> follows.terminals[it.id]
                    is Symbol.NonTerminal -> follows.nonTerminals[it.id]
                }
            }
            // Match symbol that follows
            when (val toSymbol = rule.rhs[i + 1]) {
                is Symbol.Terminal -> setsToExtend.forEach { it.add(toSymbol.id) }
                is Symbol.NonTerminal -> setsToExtend.forEach { it.addAll(initials[toSymbol.id]) }
                // Extend follow map with all initials of toSymbol
            }
        }
    }

    return follows.map { it.toTypedArray() }
}

/**
 * Compute leftmost/rightmost symbols in each nonterminal
 * @return key is NonTerminalId
 */
private fun computeExtremes(scheme: Scheme, rules: Rules, leftmostOrRightMost: Extreme): Array<Array<Symbol>> {
    // Key is NonTerminalId
    val result = Array<Array<Symbol>?>(scheme.map.nonTerminals.size) { null }
    // Key is NonTerminalId
    val visited = Array(scheme.map.nonTerminals.size) { false }

    fun visitNonTerminal(target: NonTerminalId) {
        // Prevent infinite recursion
        if (visited[target])
            throw Error("loop i think")
        visited[target] = true

        val localResult = mutableSetOf<Symbol>()
        localResult.add(Symbol.NonTerminal(target))

        for (rule in rules) {
            // Only match rules with target id
            if (rule.lhs != target) continue

            val index = if (leftmostOrRightMost == leftmost) 0 else rule.rhs.size - 1
            when (val extreme = rule.rhs[index]) {
                is Symbol.Terminal -> localResult.add(Symbol.Terminal(extreme.id))
                is Symbol.NonTerminal -> {
                    localResult.add(Symbol.NonTerminal(extreme.id))
                    // Not interested in self-recursive rules
                    if (extreme.id == target) continue
                    // If result for this NonTerminal is not present yet, compute it
                    if (result[extreme.id] == null) visitNonTerminal(extreme.id)
                    localResult.addAll(result[extreme.id]!!)
                }
            }
        }

        result[target] = localResult.toTypedArray()
    }

    // Run tree traversal for each nonterminal
    for (id in scheme.map.nonTerminals.indices) {
        visitNonTerminal(id)

        // Clear array after each traversal
        for (i in visited.indices) visited[i] = false
    }

    return result.requireNoNulls()
}

typealias Extreme = Boolean

const val leftmost = false
const val rightmost = true

/**
 * Compute leftmost terminals in each nonterminal
 * @return key is NonTerminalId
 */
private fun computeInitials(scheme: Scheme, rules: Rules): Array<Array<TerminalId>> =
    computeExtremes(scheme, rules, leftmost).map {
        it.asSequence()
            .filter { symbol -> symbol is Symbol.Terminal }
            .map { symbol -> (symbol as Symbol.Terminal).id }.toList().toTypedArray()
    }.toTypedArray()

/**
 * Compute rightmost symbols in each nonterminal
 * @return key is NonTerminalId
 */
private fun computeFinals(scheme: Scheme, rules: Rules): Array<Array<Symbol>> =
    computeExtremes(scheme, rules, rightmost)

fun mustBeAdded(rules: Rules, follow: Follow, ruleId: RuleId, lookahead: TerminalId): Boolean {
    val symbol = rules[ruleId].lhs
    return follow.nonTerminals[symbol].contains(lookahead)
}


typealias Follow = SymbolArray<Array<TerminalId>>