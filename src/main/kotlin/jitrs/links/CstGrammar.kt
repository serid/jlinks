package jitrs.links

import jitrs.links.parser.AutoCst
import jitrs.links.parser.PtToCst

/**
 * Wrapper around [Grammar] with a helper method to get Cst trees from string
 */
class CstGrammar private constructor(
    val grammar: Grammar,
    private val ptToCst: PtToCst
) {
    fun parse(string: String): AutoCst {
        val pt = grammar.parseOne(string)
        return ptToCst.convert(pt as Pt.Node)
    }

    companion object {
        fun new(
            grammar: Grammar,
            containerName: String
        ): CstGrammar {
            val ptToCst = PtToCst.new(grammar.scheme, grammar.rules, containerName)
            return CstGrammar(grammar, ptToCst)
        }
    }
}