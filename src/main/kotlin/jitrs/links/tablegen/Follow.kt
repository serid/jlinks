package jitrs.links.tablegen

import jitrs.links.tokenizer.Scheme

// For each symbol, follow set of right-most terminals are left-most terminals of the following symbol.

// Entry point
fun computeFollowMap(scheme: Scheme, rules: Rules): Follow {
    val initials = computeInitials(scheme, rules)
    val finals = computeFinals(scheme, rules)

    val follows = Array<MutableSet<TerminalId>>(scheme.map.nonTerminals.size) { mutableSetOf() }

    for (rule in rules) {
        // Iterate over symbols without last one
        for (i in 0 until (rule.rhs.size - 1)) {
            // Match symbol for which follow map is computed
            val fromSymbolsFinals = when (val fromSymbol = rule.rhs[i]) {
                is Symbol.Terminal -> continue
                is Symbol.NonTerminal -> finals[fromSymbol.id].asSequence()
            }
            // Select a set for each symbol in fromSymbol's finals
            val setsToExtend = fromSymbolsFinals.map { follows[it] }
            // Match symbol that follows
            when (val toSymbol = rule.rhs[i + 1]) {
                is Symbol.Terminal -> setsToExtend.forEach { it.add(toSymbol.id) }
                is Symbol.NonTerminal -> setsToExtend.forEach { it.addAll(initials[toSymbol.id]) }
                // Extend follow map with all initials of toSymbol
            }
        }
    }

    return follows.map { it.toTypedArray() }.toTypedArray()
}

/**
 * Compute leftmost/rightmost symbols in each nonterminal
 * @return key is NonTerminalId
 */
private fun computeExtremes(
    scheme: Scheme,
    rules: Rules,
    leftmostOrRightMost: Extreme
): Array<Array<TerminalOrNonTerminalId>> {
    // Exact type of TerminalOrNonTerminalId depends on leftMostOrRightMost
    // Key is NonTerminalId
    val result = Array<MutableSet<TerminalOrNonTerminalId>>(scheme.map.nonTerminals.size) { mutableSetOf() }
    // Key is NonTerminalId
    val visited = Array(scheme.map.nonTerminals.size) { false }

    fun visitNonTerminal(target: NonTerminalId) {
        visited[target] = true

        val localResult = result[target]
        if (leftmostOrRightMost == rightmost) localResult.add(target)

        for (rule in rules) {
            // Only match rules with target id
            if (rule.lhs != target || rule.rhs.isEmpty()) continue

            val index = if (leftmostOrRightMost == leftmost) 0 else rule.rhs.size - 1
            when (val extreme = rule.rhs[index]) {
                is Symbol.Terminal -> {
                    if (leftmostOrRightMost == leftmost)
                        localResult.add(extreme.id)
                }
                is Symbol.NonTerminal -> {
                    if (leftmostOrRightMost == rightmost)
                        localResult.add(extreme.id)
                    // Recurse if not visited
                    if (!visited[extreme.id]) {
                        visitNonTerminal(extreme.id)
                    }
                    // If result for this NonTerminal is present, add it
                    localResult.addAll(result[extreme.id])
                }
            }
        }
    }

    // Run tree traversal for each nonterminal
    for (id in scheme.map.nonTerminals.indices) {
        visitNonTerminal(id)

        // Clear array after each traversal
        // TODO: this hurts performance, someone should investigate whether this line is needed
        for (i in visited.indices) visited[i] = false
    }

    return result.map { it.toTypedArray() }.toTypedArray()
}

typealias Extreme = Boolean

const val leftmost = false
const val rightmost = true

typealias TerminalOrNonTerminalId = Int

/**
 * Compute leftmost terminals in each nonterminal
 * @return key is NonTerminalId
 */
private fun computeInitials(scheme: Scheme, rules: Rules): Array<Array<TerminalId>> =
    computeExtremes(scheme, rules, leftmost)

/**
 * Compute rightmost symbols in each nonterminal
 * @return key is NonTerminalId
 */
private fun computeFinals(scheme: Scheme, rules: Rules): Array<Array<NonTerminalId>> =
    computeExtremes(scheme, rules, rightmost)

fun mustBeAdded(rules: Rules, follow: Follow, ruleId: RuleId, lookahead: TerminalId): Boolean {
    val symbol = rules[ruleId].lhs
    return follow[symbol].contains(lookahead)
}

// Key is NonTerminalId
typealias Follow = Array<Array<TerminalId>>