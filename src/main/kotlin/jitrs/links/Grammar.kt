package jitrs.links

import jitrs.links.parser.parseOne
import jitrs.links.tablegen.generateTable
import jitrs.util.ArrayIterator

class Grammar private constructor(
    private val scheme: Scheme,
    private val rules: Rules,
    private val table: Table,
    private val identStartPredicate: (Char) -> Boolean,
    private val identPartPredicate: (Char) -> Boolean,
    private val debug: Boolean,
) {
    fun parseOne(string: String): Cst {
        val tokens = tokenize(scheme.map.terminals, string, identStartPredicate, identPartPredicate)
        return parseOne(table, rules, ArrayIterator(tokens), debug)
    }

    fun cstToString(cst: Cst) = cst.toString(scheme)

    companion object {
        fun new(
            terminals: Array<String>,
            nonTerminals: Array<String>,
            rules: String,
            identStartPredicate: (Char) -> Boolean,
            identPartPredicate: (Char) -> Boolean,
            debug: Boolean = true,
        ): Grammar {
            val scheme = Scheme(
                SymbolArray(
                    terminals,
                    nonTerminals
                )
            )
            val rules1 = metaParse(scheme, rules)
            val table = generateTable(scheme, rules1)
            return Grammar(scheme, rules1, table, identStartPredicate, identPartPredicate, debug)
        }
    }
}