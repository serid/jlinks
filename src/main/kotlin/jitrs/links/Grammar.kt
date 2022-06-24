package jitrs.links

import jitrs.links.parser.AutoCst
import jitrs.links.parser.PtToCst
import jitrs.links.parser.parse
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
    private val ptToCst: PtToCst?,
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

    fun parseOneCst(string: String): AutoCst {
        val pt = parseOne(string)
        return ptToCst!!.convert(pt as Pt.Node)
    }

    companion object {
        /**
         * @param containerName Name of a Java package or Class where Cst classes reside.
         * Required for parsing into Cst with [parseOneCst].
         */
        fun new(
            terminals: Array<String>,
            nonTerminals: Array<String>,
            rules: String,
            containerName: String? = null,
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

            // If a container name was provided, prepare PtToCst converter
            val ptToCstEnabled = containerName != null

            val (rules1, ruleIdConstructorMap) = metaParse(scheme, rules, ptToCstEnabled)
            val table = generateTable(scheme, rules1)

            val ptToCst = if (ptToCstEnabled)
                PtToCst.new(scheme, rules1, ruleIdConstructorMap!!, containerName!!)
            else
                null
            return Grammar(scheme, rules1, table, ptToCst, identStartPredicate, identPartPredicate, debug)
        }
    }
}