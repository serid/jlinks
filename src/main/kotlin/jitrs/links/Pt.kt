package jitrs.links

import jitrs.links.tablegen.NonTerminalId
import jitrs.links.tablegen.RuleId
import jitrs.links.tokenizer.Scheme
import jitrs.links.tokenizer.Token

// Generic parse tree for results of parsing
sealed class Pt {
    data class Leaf(
        val token: Token
    ) : Pt()

    data class Node(
        val id: NonTerminalId,
        val ruleId: RuleId,
        val children: Array<Pt>,
    ) : Pt()

    fun toString(scheme: Scheme): String {
        return when (this) {
            is Leaf -> {
                this.token.toString(scheme)
            }
            is Node -> {
                val s = scheme.map.nonTerminals[this.id]
                this.children.joinToString(",", "$s:$ruleId[", "]") { it.toString(scheme) }
            }
        }
    }
}