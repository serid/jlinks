package jitrs.links

import jitrs.links.parser.parse
import jitrs.links.tablegen.Rules
import jitrs.links.tablegen.SymbolArray
import jitrs.links.tablegen.Table
import jitrs.links.tablegen.generateTable
import jitrs.links.tokenizer.Scheme
import jitrs.links.tokenizer.tokenize
import jitrs.util.ArrayIterator

@Suppress("MemberVisibilityCanBePrivate")
class Grammar private constructor(
    val scheme: Scheme,
    val rules: Rules,
    val table: Table,
    private val identStartPredicate: (Char) -> Boolean,
    private val identPartPredicate: (Char) -> Boolean,
    private val debug: Boolean,
) {
    fun parseMany(string: String): Array<Pt> {
        val tokens = tokenize(scheme, string, identStartPredicate, identPartPredicate)
        return parse(scheme, table, rules, ArrayIterator(tokens), string, returnFirstParse = false, debug)
    }

    fun parseOne(string: String): Pt {
        val tokens = tokenize(scheme, string, identStartPredicate, identPartPredicate)
        return parse(scheme, table, rules, ArrayIterator(tokens), string, returnFirstParse = true, debug)[0]
    }

    companion object {
        fun new(
            terminals: Array<String>,
            nonTerminals: Array<String>,
            rules: String,
            identStartPredicate: (Char) -> Boolean = { false },
            identPartPredicate: (Char) -> Boolean = { false },
            debug: Boolean = true,
        ): Grammar {
            val scheme = Scheme.new(
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