package jitrs.links

import jitrs.links.util.UnreachableError
import jitrs.links.util.matchPrefix

// Terminals "<int>", "<id>", "<string>" and "<eof>" have special meaning for the tokenizer.
// Use escape prefix $ to treat them as normal lexemes.
fun tokenize(terminals: Array<String>, string: String, identCharacterPredicate: (Char) -> Boolean): Array<Token> {
    // Split terminals into "lexemes" and "special"
    val lexemeTerminals0 = arrayListOf<Pair<TerminalId, String>>()

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
        else -> lexemeTerminals0.add(Pair(i, unEscapeTerminal(str)))
    }
    val lexemeTerminals = lexemeTerminals0.toTypedArray()


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
            result.add(Token(id, Token.Data.LexemeToken))
            i += lexeme.length
            continue@outer
        }

        // Try specials
        when {
            intSpecialId != -1 && Character.isDigit(string[i]) -> {
                var n = 0
                while (i < string.length && Character.isDigit(string[i])) {
                    n *= 10
                    n += string[i].digitToInt()
                    i++
                }
                result.add(Token(intSpecialId, Token.Data.IntToken(n)))
            }
            identSpecialId != -1 && identCharacterPredicate(string[i]) -> {
                val s = StringBuilder()
                while (i < string.length && identCharacterPredicate(string[i])) {
                    s.append(string[i])
                    i++
                }
                result.add(Token(identSpecialId, Token.Data.IdentToken(s.toString())))
            }
            stringSpecialId != -1 && string[i] == '"' -> {
                val s = StringBuilder()
                while (i < string.length && string[i] != '"') {
                    s.append(string[i])
                    i++
                }
                if (i == string.length) throw RuntimeException("expected ending quote")
                result.add(Token(stringSpecialId, Token.Data.StringToken(s.toString())))
            }
            else -> throw RuntimeException("Unrecognized token")
        }
    }
    result.add(Token(eofSpecialId, Token.Data.LexemeToken))

    return result.toTypedArray()
}

fun isSpecialTerminal(string: String) = when (string) {
    "<int>", "<id>", "<string>", "<eof>" -> true
    else -> false
}

fun escapeSpecialTerminal(string: String): String = if (isSpecialTerminal(string)) "$$string" else string

fun unEscapeTerminal(string: String): String = string.removePrefix("$")


// maps token ids to readable names
data class Scheme(
    val map: SymbolArray<String>
) {
    init {
        sortTerminals(map.terminals)
    }

    val reverse: HashMap<String, Symbol> by lazy {
        val h = hashMapOf<String, Symbol>()
        for ((id, str) in map.terminals.withIndex()) h[str] = Symbol.Terminal(id)
        for ((id, str) in map.nonTerminals.withIndex()) h[str] = Symbol.NonTerminal(id)
        h
    }

    companion object {
        // Sort terminals in order of decreasing length
        // Without sorting, tokenization of "integer" in language ("int", "integer") yields Token(INT) and tail "eger"
        fun sortTerminals(terminals: Array<String>): Unit = terminals.sortWith { x, y -> y.length - x.length }
    }
}

// generic token
data class Token(
    val id: TerminalId,
    val data: Data
) {
    sealed class Data {
        data class IntToken(val data: Int) : Data()
        data class IdentToken(val data: String) : Data()
        data class StringToken(val data: String) : Data()
        object LexemeToken : Data()
    }

    fun toString(scheme: Scheme): String {
        val s = scheme.map.terminals[this.id]
        return if (this.data is Data.LexemeToken) {
            s
        } else {
            val s2 = when (this.data) {
                is Data.IntToken -> data.data.toString()
                is Data.IdentToken -> data.data
                is Data.StringToken -> "\"${data.data}\""
                is Data.LexemeToken -> throw UnreachableError()
            }
            "($s:$s2)"
        }
    }
}
