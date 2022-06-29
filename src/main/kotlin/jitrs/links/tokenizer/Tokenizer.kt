package jitrs.links.tokenizer

import jitrs.links.tablegen.TerminalId
import jitrs.util.matchPrefix

fun tokenize(
    terminals: Array<String>,
    specialIdInfo: SpecialIdInfo,
    string: String,
    identStartPredicate: (Char) -> Boolean = { false },
    identPartPredicate: (Char) -> Boolean = identStartPredicate
): Array<Token> {
    // Split terminals into "keywords" and "special"
    val keywordTerminals = terminals.asSequence()
        .withIndex()
        .filter { (id, _) -> !specialIdInfo.isSpecialTerminal(id) }


    val result = arrayListOf<Token>()
    var i = 0
    outer@
    while (i < string.length) {
        if (string[i] == ' ') {
            i++
            continue@outer
        }

        // TODO: use FSM instead of trying each keyword
        // Try keywords
        for ((id, keyword) in keywordTerminals) {
            // If last keyword character and the next character have same class, it's not a full keyword but a prefix
            if (i + keyword.length < string.length &&
                sameCharClass(keyword.last(), string[i + keyword.length])
            ) continue
            if (!matchPrefix(string, i, keyword)) continue

            result.add(Token(id, Unit, Span(i, i + keyword.length)))
            i += keyword.length
            continue@outer
        }

        // Try specials
        when {
            specialIdInfo.newlineSpecialId != -1 && string[i] == '\n' -> {
                result.add(Token(specialIdInfo.newlineSpecialId, Unit, Span(i, i + 1)))
                i++
            }
            specialIdInfo.intSpecialId != -1 && Character.isDigit(string[i]) -> {
                val start = i

                var n = 0
                do {
                    n *= 10
                    n += string[i].digitToInt()
                    i++
                } while (i < string.length && Character.isDigit(string[i]))
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
                    throw SyntaxErrorException("Expected ending quote", string, Span(i, i))
                i++

                result.add(Token(specialIdInfo.stringSpecialId, s.toString(), Span(start, i)))
            }
            else -> throw SyntaxErrorException("Unrecognized token", string, Span(i, i))
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

//fun lexicalAnalysis(string: String): Iterator<Span> = iterator {
//    var i = 0
//    outer@
//    while (true) {
//        // skip space
//        while (true) {
//            if (i >= string.length) break@outer
//            if (string[i] != ' ') break
//            i++
//        }
//
//        // Count until lexeme end
//        var j = i + 1
//        while (true) {
//            if (j >= string.length) break
//            if (!sameCharClass(string[j - 1], string[j])) break
//            j++
//        }
//
//        yield(Span(i, j))
//    }
//}

private fun sameCharClass(c1: Char, c2: Char): Boolean = c1.classify() == c2.classify()

private fun Char.classify(): Int = when {
    this.isLetterOrDigit() -> 1
    this == '\n' -> 2
    this.isWhitespace() -> 3
    else -> 4
}

fun String.regionFullyMatches(from: Int, to: Int, other: String): Boolean =
    (to - from == other.length) && this.regionMatches(from, other, 0, other.length)

fun escapeSpecialTerminal(
    specialIdInfo: SpecialIdInfo,
    id: TerminalId,
    string: String
): String = if (specialIdInfo.isSpecialTerminal(id)) "$$string" else string

fun unEscapeTerminal(string: String): String = string.removePrefix("$")