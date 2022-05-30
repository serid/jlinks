package jitrs.links

typealias TerminalId = Int
typealias NonTerminalId = Int

typealias StateId = Int
typealias RuleId = Int

data class Table(
    // key is jitrs.links.StateId
    val map: Array<Row>,
) {
    override fun toString(): String {
        return map.joinToString(separator = ",\n") { it.toString() }
    }

    fun isUnambiguous(): Boolean = map.all { row ->
        row.action.all { action ->
            action !is Action.Fork
        }
    }

    // Generated

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Table

        if (!map.contentEquals(other.map)) return false

        return true
    }

    override fun hashCode(): Int {
        return map.contentHashCode()
    }
}

data class Row(
    // key is jitrs.links.TerminalId
    val action: Array<Action>,
    // key is jitrs.links.NonTerminalId
    val goto: Array<StateId>,
) {
    // Generated

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Row

        if (!action.contentEquals(other.action)) return false
        if (!goto.contentEquals(other.goto)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = action.contentHashCode()
        result = 31 * result + goto.contentHashCode()
        return result
    }
}

sealed class Action {
    data class Just(val action: StackAction) : Action() {
        override fun toString(): String = action.toString()
    }

    data class Fork(val actions: Array<StackAction>) : Action() {
        override fun toString(): String = actions.contentToString()
    }

    object Error : Action() {
        override fun toString(): String = "__"
    }

    object Done : Action() {
        override fun toString(): String = "Done"
    }
}

sealed class StackAction {
    data class Shift(val state: StateId) : StackAction() {
        override fun toString(): String = "s$state"
    }

    data class Reduce(val id: RuleId) : StackAction() {
        override fun toString(): String = "r$id"
    }
}

// first element is LHS, RHS symbols are terminals/nonterminals
// NOTE: the parsing loop can work without specifying precise rules, only LHS and number of symbols are needed.
// the symbols are still encoded to enable runtime correctness assertions
data class Rule(
    val lhs: NonTerminalId,
    val rhs: Array<Symbol>,
) {
    override fun toString(): String {
        return lhs.toString() + " -> " + rhs.contentToString()
    }

    // Generated

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Rule

        if (lhs != other.lhs) return false
        if (!rhs.contentEquals(other.rhs)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lhs
        result = 31 * result + rhs.contentHashCode()
        return result
    }
}
typealias Rules = Array<Rule>

// Array indexed using symbols
data class SymbolArray<T>(
    // Key is TerminalId
    val terminals: Array<T>,
    // Key is NonTerminalId
    val nonTerminals: Array<T>,
) {
    inline fun <reified U> newOf(noinline f: () -> U): SymbolArray<U> = this.withArray { Array(it) { f() } }

    fun <U> withArray(f: (Int) -> Array<U>): SymbolArray<U> = SymbolArray(
        f(terminals.size),
        f(nonTerminals.size),
    )

    inline fun <reified U> map(f: (T) -> U): SymbolArray<U> = SymbolArray(
        terminals.map(f).toTypedArray(),
        nonTerminals.map(f).toTypedArray(),
    )

    fun iter(): Sequence<Pair<Symbol, T>> {
        val terminals = this.terminals.asSequence().withIndex().map { (id, term) -> Pair(Symbol.Terminal(id), term) }
        val nonTerminals =
            this.nonTerminals.asSequence().withIndex().map { (id, term) -> Pair(Symbol.NonTerminal(id), term) }
        return terminals.plus(nonTerminals)
    }
}

sealed class Symbol {
    data class Terminal(val id: TerminalId) : Symbol()
    data class NonTerminal(val id: NonTerminalId) : Symbol()

    fun compareWithNode(cst: Cst): Boolean =
        ((this as? Terminal)?.id == (cst as? Cst.Leaf)?.token?.id) ||
                ((this as? NonTerminal)?.id == (cst as? Cst.Node)?.id)
}