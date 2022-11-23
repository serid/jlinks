package jitrs.links.tokenizer

import jitrs.links.tablegen.TerminalId
import jitrs.util.matchPrefix

fun tokenize(
    terminals: Array<String>,
    specialIdInfo: SpecialIdInfo,
    string: String,
    identStartPredicate: (Char) -> Boolean = { false },
    identPartPredicate: (Char) -> Boolean = identStartPredicate,
    treatNewLineAsSpace: Boolean = true,
): Array<Token> {
    fun isWhitespace(c: Char): Boolean =
        c == ' ' || treatNewLineAsSpace && specialIdInfo.newlineSpecialId == -1 && c == '\n'

    // Split terminals into "keywords" and "special"
    val keywordTerminals = terminals.asSequence()
        .withIndex()
        .filter { (id, _) -> !specialIdInfo.isSpecialTerminal(id) }


    val result = arrayListOf<Token>()
    var i = 0
    outer@
    while (true) {
        // Skip space
        while (i < string.length && isWhitespace(string[i]))
            i++

        // Skip comments
        if (i < string.length && matchPrefix(string, i, "--")) {
            i += 2
            while (i < string.length && string[i] != '\n')
                i++
        }

        if (i >= string.length)
            break

        // Find the longest matching token among keywords and specials
        // Sequence of tokens and their lengths, which is then searched for a maximum value
        val possibleTokens = sequence<Pair<Token, Int>> {
            // TODO: use FSM instead of trying each keyword
            // Try keywords
            for ((id, keyword) in keywordTerminals) {
                if (!matchPrefix(string, i, keyword)) continue

                yield(Pair(Token(id, Unit, Span(i, i + keyword.length)), keyword.length))
            }

            // Try specials
            when {
                specialIdInfo.newlineSpecialId != -1 && string[i] == '\n' -> {
                    yield(Pair(Token(specialIdInfo.newlineSpecialId, Unit, Span(i, i + 1)), 1))
                }

                specialIdInfo.intSpecialId != -1 && Character.isDigit(string[i]) -> {
                    val start = i
                    var k = i

                    var n = 0
                    do {
                        n *= 10
                        n += string[k].digitToInt()
                        k++
                    } while (k < string.length && Character.isDigit(string[k]))
                    yield(Pair(Token(specialIdInfo.intSpecialId, n, Span(start, k)), k - start))
                }

                specialIdInfo.identSpecialId != -1 && identStartPredicate(string[i]) -> {
                    val start = i
                    var k = i

                    val s = StringBuilder()
                    do {
                        s.append(string[k])
                        k++
                    } while (k < string.length && identPartPredicate(string[k]))
                    yield(Pair(Token(specialIdInfo.identSpecialId, s.toString(), Span(start, k)), k - start))
                }

                specialIdInfo.stringSpecialId != -1 && string[i] == '"' -> {
                    val start = i
                    var k = i

                    k++
                    val s = StringBuilder()
                    while (k < string.length && string[k] != '"') {
                        s.append(string[k])
                        k++
                    }
                    if (k == string.length && string[k - 1] != '"')
                        throw SyntaxErrorException("Expected ending quote", string, Span(k, k))
                    k++

                    yield(Pair(Token(specialIdInfo.stringSpecialId, s.toString(), Span(start, k)), k - start))
                }
            }
        }

        val (longestToken, longestTokenLength) = possibleTokens.maxByOrNull { it.second }
            ?: throw SyntaxErrorException("Unrecognized token", string, Span(i, i))

        result.add(longestToken)
        i += longestTokenLength
    }
    result.add(Token(specialIdInfo.eofSpecialId, Unit, Span(i, i)))

    return result.toTypedArray()
}

fun tokenize(
    scheme: Scheme,
    string: String,
    identStartPredicate: (Char) -> Boolean = { false },
    identPartPredicate: (Char) -> Boolean = identStartPredicate,
    treatNewLineAsSpace: Boolean = true,
): Array<Token> = tokenize(
    scheme.map.terminals,
    scheme.specialIdInfo,
    string,
    identStartPredicate,
    identPartPredicate,
    treatNewLineAsSpace,
)

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

//private fun sameCharClass(c1: Char, c2: Char): Boolean = c1.classify() == c2.classify()
//
//private fun Char.classify(): Int = when {
//    this.isLetterOrDigit() -> 1
//    this == '\n' -> 2
//    this.isWhitespace() -> 3
//    else -> 4
//}

fun String.regionFullyMatches(from: Int, to: Int, other: String): Boolean =
    (to - from == other.length) && this.regionMatches(from, other, 0, other.length)

fun escapeSpecialTerminal(
    specialIdInfo: SpecialIdInfo,
    id: TerminalId,
    string: String
): String = if (specialIdInfo.isSpecialTerminal(id)) "$$string" else string

fun unEscapeTerminal(string: String): String = string.removePrefix("$")