package jitrs.links

import jitrs.links.util.ArrayIterator
import jitrs.links.util.matchPrefix

/**
 * Parse a string into an array of parsing rules
 */
fun metaParse(scheme: Scheme, string: String): Rules {
    val symbolIterator = ArrayIterator(scheme.map.iter().toList().toTypedArray())

    val rules = ArrayList<Rule>()
    var lhs: NonTerminalId = -1
    val rhs = ArrayList<Symbol>()

    var i = 0

    // Loop over rule lines
    outer@
    while (true) {
        i = skipWhiteSpace(string, i)
        if (i >= string.length) break

        // Read rule lhs
        for ((id, lexeme) in scheme.map.nonTerminals.withIndex())
            if (matchPrefix(string, i, lexeme)) {
                lhs = id
                i += lexeme.length
                break
            }
        if (lhs == -1) throw RuntimeException("lhs not recognized")

        // Match arrow
        if (!matchPrefix(string, i, " -> ")) break@outer
        i += " -> ".length

        // Read symbols
        inner@ while (true) {
            i = skipSpace(string, i)
            if (i >= string.length) break

            if (string[i] == '\n') {
                rules.add(Rule(lhs, rhs.toTypedArray()))
                i++

                lhs = -1
                rhs.clear()
                break
            }

            symbolIterator.reset()
            for ((symbol, lexeme) in symbolIterator)
                if (matchPrefix(string, i, lexeme)) {
                    rhs.add(symbol)
                    i += lexeme.length

                    continue@inner
                }
            throw RuntimeException("Unrecognized symbol at index \"$i\"")
        }
    }

    return rules.toTypedArray()
}

fun skipSpace(string: String, i: Int): Int {
    var i2 = i
    while (i2 < string.length && string[i2] == ' ') i2++
    return i2
}

fun skipWhiteSpace(string: String, i: Int): Int {
    var i2 = i
    while (i2 < string.length && (string[i2] == ' ' || string[i2] == '\n')) i2++
    return i2
}