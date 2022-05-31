package jitrs.links

import jitrs.util.UnreachableError
import jitrs.util.matchPrefix
import jitrs.util.myAssert

// Terminals "<int>", "<id>", "<string>" and "<eof>" have special meaning for the tokenizer.
// Use escape prefix $ to treat them as normal lexemes.
fun tokenize(
    terminals: Array<String>,
    specialIdInfo: SpecialIdInfo,
    string: String,
    identStartPredicate: (Char) -> Boolean = { false },
    identPartPredicate: (Char) -> Boolean = { false }
): Array<Token> {
    // Split terminals into "lexemes" and "special"
    val lexemeTerminals = terminals.asSequence()
        .withIndex()
        .filter { (_, str) -> !isSpecialTerminal(str) }
        .map { (i, str) -> Pair(i, unEscapeTerminal(str)) }
        .toList().toTypedArray()


    val result = arrayListOf<Token>()
    var i = 0
    outer@
    while (i < string.length) {
        if (string[i] == ' ') {
            i++
            continue@outer
        }

        // Try lexemes
        for ((id, lexeme) in lexemeTerminals) if (matchPrefix(string, i, lexeme)) {
            result.add(Token(id, Unit))
            i += lexeme.length
            continue@outer
        }

        // Try specials
        when {
            specialIdInfo.intSpecialId != -1 && Character.isDigit(string[i]) -> {
                var n = 0
                while (i < string.length && Character.isDigit(string[i])) {
                    n *= 10
                    n += string[i].digitToInt()
                    i++
                }
                result.add(Token(specialIdInfo.intSpecialId, n))
            }
            specialIdInfo.identSpecialId != -1 && identStartPredicate(string[i]) -> {
                val s = StringBuilder()
                do {
                    s.append(string[i])
                    i++
                } while (i < string.length && identPartPredicate(string[i]))
                result.add(Token(specialIdInfo.identSpecialId, s.toString()))
            }
            specialIdInfo.stringSpecialId != -1 && string[i] == '"' -> {
                val s = StringBuilder()
                while (i < string.length && string[i] != '"') {
                    s.append(string[i])
                    i++
                }
                if (i == string.length) throw RuntimeException("expected ending quote")
                result.add(Token(specialIdInfo.stringSpecialId, s.toString()))
            }
            else -> throw RuntimeException("Unrecognized token")
        }
    }
    result.add(Token(specialIdInfo.eofSpecialId, Unit))

    return result.toTypedArray()
}

fun tokenize(
    scheme: Scheme,
    string: String,
    identStartPredicate: (Char) -> Boolean = { false },
    identPartPredicate: (Char) -> Boolean = { false }
): Array<Token> = tokenize(scheme.map.terminals, scheme.specialIdInfo, string, identStartPredicate, identPartPredicate)

fun isSpecialTerminal(string: String) = when (string) {
    "<int>", "<id>", "<string>", "<eof>" -> true
    else -> false
}

fun escapeSpecialTerminal(string: String): String = if (isSpecialTerminal(string)) "$$string" else string

fun unEscapeTerminal(string: String): String = string.removePrefix("$")


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
) {
    @Suppress("NOTHING_TO_INLINE")
    inline fun isTerminalWithData(id: TerminalId): Boolean =
        id == intSpecialId ||
                id == identSpecialId ||
                id == stringSpecialId

    @Suppress("NOTHING_TO_INLINE")
    inline fun isSpecialTerminal(id: TerminalId): Boolean = isTerminalWithData(id) || id == eofSpecialId

    companion object {
        fun from(terminals: Array<String>): SpecialIdInfo {
            // Find ids of special terminals
            var intSpecialId: TerminalId = -1
            var identSpecialId: TerminalId = -1
            var stringSpecialId: TerminalId = -1
            var eofSpecialId: TerminalId = -1
            for ((i, str) in terminals.withIndex()) when (str) {
                "<int>" -> intSpecialId = i
                "<id>" -> identSpecialId = i
                "<string>" -> stringSpecialId = i
                "<eof>" -> eofSpecialId = i
            }
            return SpecialIdInfo(
                intSpecialId,
                identSpecialId,
                stringSpecialId,
                eofSpecialId
            )
        }
    }
}

// generic token
data class Token(
    val id: TerminalId,
    val data: Any // Int | String | Unit. Interpretation depends on id
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
