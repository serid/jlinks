package jitrs.links.tokenizer

import jitrs.links.tablegen.Symbol
import jitrs.links.tablegen.SymbolArray
import jitrs.links.tablegen.TerminalId
import jitrs.util.UnreachableError
import jitrs.util.myAssert

// maps token ids to readable names
class Scheme private constructor(
    val map: SymbolArray<String>,
    val specialIdInfo: SpecialIdInfo,
) {
    val reverse: HashMap<String, Symbol> by lazy {
        val h = hashMapOf<String, Symbol>()
        for ((id, str) in map.terminals.withIndex()) h[str] = Symbol.Terminal(id)
        for ((id, str) in map.nonTerminals.withIndex()) h[str] = Symbol.NonTerminal(id)
        h
    }

    companion object {
        fun new(
            map: SymbolArray<String>
        ): Scheme {
            // Sort terminals
            sortTerminals(map.terminals)

            return Scheme(
                map,
                SpecialIdInfo.from(map.terminals)
            )
        }

        // Sort terminals in order of decreasing length
        // Without sorting, tokenization of "integer" in language ("int", "integer") yields Token(INT) and tail "eger"
        fun sortTerminals(terminals: Array<String>): Unit = terminals.sortWith { x, y -> y.length - x.length }
    }
}

data class SpecialIdInfo(
    val intSpecialId: TerminalId,
    val identSpecialId: TerminalId,
    val stringSpecialId: TerminalId,
    val eofSpecialId: TerminalId,
    val newlineSpecialId: TerminalId,
) {
    @Suppress("NOTHING_TO_INLINE")
    inline fun isTerminalWithData(id: TerminalId): Boolean =
        id == intSpecialId ||
                id == identSpecialId ||
                id == stringSpecialId

    @Suppress("NOTHING_TO_INLINE")
    inline fun isSpecialTerminal(id: TerminalId): Boolean =
        isTerminalWithData(id) || id == eofSpecialId || id == newlineSpecialId

    companion object {
        fun from(terminals: Array<String>): SpecialIdInfo {
            // Find ids of special terminals
            var intSpecialId: TerminalId = -1
            var identSpecialId: TerminalId = -1
            var stringSpecialId: TerminalId = -1
            var eofSpecialId: TerminalId = -1
            var newlineSpecialId: TerminalId = -1
            for ((i, str) in terminals.withIndex()) when (str) {
                "<int>" -> intSpecialId = i
                "<id>" -> identSpecialId = i
                "<string>" -> stringSpecialId = i
                "<eof>" -> eofSpecialId = i
                "<nl>" -> newlineSpecialId = i
            }
            return SpecialIdInfo(
                intSpecialId,
                identSpecialId,
                stringSpecialId,
                eofSpecialId,
                newlineSpecialId
            )
        }
    }
}

// generic token
data class Token(
    val id: TerminalId,
    val data: Any, // Int | String | Unit. Interpretation depends on id
    val span: Span
) {
    init {
        myAssert(data is Int || data is String || data is Unit)
    }

    fun toString(scheme: Scheme): String {
        val s = scheme.map.terminals[this.id]
        return if (!scheme.specialIdInfo.isTerminalWithData(this.id)) {
            s
        } else {
            val s2 = when (this.id) {
                scheme.specialIdInfo.intSpecialId -> data.toString()
                scheme.specialIdInfo.identSpecialId -> data as String
                scheme.specialIdInfo.stringSpecialId -> "\"$data\""
                else -> throw UnreachableError()
            }
            "($s:$s2)"
        }
    }
}

data class Span(
    val start: Int,
    val end: Int
) {
    override fun toString(): String = "$start:$end"
}