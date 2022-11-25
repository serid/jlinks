package jitrs.links

import jitrs.links.parser.*
import jitrs.links.tablegen.Rules
import jitrs.links.tablegen.SymbolArray
import jitrs.links.tablegen.Table
import jitrs.links.tablegen.generateTable
import jitrs.links.tokenizer.Scheme
import jitrs.links.tokenizer.tokenize
import jitrs.util.ArrayIterator

class Grammar private constructor(
    val scheme: Scheme,
    val rules: Rules,
    val table: Table,
    private val reductor: LRReductor,
    private val identStartPredicate: (Char) -> Boolean,
    private val identPartPredicate: (Char) -> Boolean,
    private val debug: Boolean,
) {
    fun parseMany(string: String): Array<AutoCst> {
        val tokens = tokenize(scheme, string, identStartPredicate, identPartPredicate)
        return parse(scheme, table, rules, reductor, ArrayIterator(tokens), string, returnFirstParse = false, debug)
    }

    fun parseOne(string: String): AutoCst {
        val tokens = tokenize(scheme, string, identStartPredicate, identPartPredicate)
        return parse(scheme, table, rules, reductor, ArrayIterator(tokens), string, returnFirstParse = true, debug)[0]
    }

    companion object {
        /**
         * @param containerName Name of a Java package or Class where Cst classes reside.
         * If not provided, parser will return a stringified Cst.
         */
        fun new(
            terminals: Array<String>,
            nonTerminals: Array<String>,
            rules: String,
            containerName: String? = null,
            identStartPredicate: (Char) -> Boolean = { false },
            identPartPredicate: (Char) -> Boolean = identStartPredicate,
            debug: Boolean = true,
        ): Grammar {
            val scheme = Scheme(SymbolArray(terminals, nonTerminals))

            // If a container name was provided, prepare LRToCst converter
            val toCstEnabled = containerName != null

            val (rules1, ruleIdConstructorMap) = metaParse(scheme, rules, toCstEnabled)
            val table = generateTable(scheme, rules1)

            val lrReductor = if (toCstEnabled)
                LRToCst(scheme, rules1, ruleIdConstructorMap!!, containerName!!)
            else
                LRToString(scheme)
            return Grammar(scheme, rules1, table, lrReductor, identStartPredicate, identPartPredicate, debug)
        }
    }
}