package jitrs.links

// Generic syntax tree for results of parsing
sealed class Cst {
    data class Leaf(
        val token: Token
    ) : Cst()

    data class Node(
        val id: NonTerminalId,
        val ruleId: RuleId,
        val children: Array<Cst>,
    ) : Cst()

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