package jitrs.links.tokenizer

import jitrs.links.tablegen.TerminalId
import jitrs.util.matchPrefix

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
        .filter { (id, _) -> !specialIdInfo.isSpecialTerminal(id) }
        .map { (id, str) -> Pair(id, unEscapeTerminal(str)) }
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
            result.add(Token(id, Unit, Span(i, i + lexeme.length)))
            i += lexeme.length
            continue@outer
        }

        // Try specials
        when {
            specialIdInfo.intSpecialId != -1 && Character.isDigit(string[i]) -> {
                val start = i

                var n = 0
                while (i < string.length && Character.isDigit(string[i])) {
                    n *= 10
                    n += string[i].digitToInt()
                    i++
                }
                result.add(Token(specialIdInfo.intSpecialId, n, Span(start, i)))
            }
            specialIdInfo.identSpecialId != -1 && identStartPredicate(string[i]) -> {
                val start = i

                val s = StringBuilder()
                do {
                    s.append(string[i])
                    i++
                } while (i < string.length && identPartPredicate(string[i]))
                result.add(Token(specialIdInfo.identSpecialId, s.toString(), Span(start, i)))
            }
            specialIdInfo.stringSpecialId != -1 && string[i] == '"' -> {
                val start = i

                i++
                val s = StringBuilder()
                while (i < string.length && string[i] != '"') {
                    s.append(string[i])
                    i++
                }
                if (i == string.length && string[i - 1] != '"')
                    throw SyntaxErrorException("Expected ending quote", Span(i, i))
                i++

                result.add(Token(specialIdInfo.stringSpecialId, s.toString(), Span(start, i)))
            }
            else -> throw SyntaxErrorException("Unrecognized token", Span(i, i))
        }
    }
    result.add(Token(specialIdInfo.eofSpecialId, Unit, Span(i, i)))

    return result.toTypedArray()
}

fun tokenize(
    scheme: Scheme,
    string: String,
    identStartPredicate: (Char) -> Boolean = { false },
    identPartPredicate: (Char) -> Boolean = { false }
): Array<Token> = tokenize(scheme.map.terminals, scheme.specialIdInfo, string, identStartPredicate, identPartPredicate)

fun escapeSpecialTerminal(
    specialIdInfo: SpecialIdInfo,
    id: TerminalId,
    string: String
): String = if (specialIdInfo.isSpecialTerminal(id)) "$$string" else string

fun unEscapeTerminal(string: String): String = string.removePrefix("$")