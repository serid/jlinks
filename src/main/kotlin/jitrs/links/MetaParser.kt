package jitrs.links

import jitrs.links.tablegen.*
import jitrs.links.tokenizer.*

/**
 * Parse a string into an array of parsing rules
 */
fun metaParse(scheme: Scheme, string: String): Rules {
    // Build a language with terminals required for parsing rules
    // In this language what was nonterminals is treated as terminals
    val terminalNonTerminals = scheme.map.nonTerminals.asSequence()
    val language = scheme.map.terminals.asSequence()
        .withIndex()
        .map { (id, str) -> escapeSpecialTerminal(scheme.specialIdInfo, id, str) }
        .plus(sequenceOf("->", "\n", "<eof>"))
        .plus(terminalNonTerminals)
        .toList().toTypedArray()

    Scheme.sortTerminals(language)

    val arrowId: TerminalId = language.indexOf("->")
    val newLineId: TerminalId = language.indexOf("\n")
    val metaEofId: TerminalId = language.indexOf("<eof>")
    // Not to be confused with escaped "$<eof>" already present in scheme

    // Maps terminals in new language into symbols of original scheme
    val backMap = Array<Symbol?>(language.size) { null }
    for ((id, lex) in language.withIndex()) {
        val unescaped = unEscapeTerminal(lex)

        fun f(array: Array<String>, f: (Int) -> Symbol) {
            val schemeId = array.indexOf(unescaped)
            if (schemeId != -1)
                backMap[id] = f(schemeId)
        }

        f(scheme.map.terminals, Symbol::Terminal)
        f(scheme.map.nonTerminals, Symbol::NonTerminal)
    }

    // Assert that every terminal except "\n" and "->" maps to a symbol in original scheme
    backMap.asSequence().withIndex()
        .filter { (id, _) -> id != newLineId && id != arrowId }
        .map { (_, symbol) -> symbol }.requireNoNulls()

    val tokens = tokenize(language, SpecialIdInfo.from(language), string)

    // State machine for parsing rules
    val stateLhs = 0 // Reading lhs
    val stateArrow = 1 // Reading arrow
    val stateRhs = 2 // Reading rhs
    var state = stateLhs

    val rules = ArrayList<Rule>()
    var lhs: NonTerminalId = -1
    val rhs = ArrayList<Symbol>()
    for ((tokenId, _, span) in tokens) {
        when (state) {
            stateLhs -> {
                if (tokenId == newLineId || tokenId == metaEofId) continue
                when (val lhs0 = backMap[tokenId]) {
                    is Symbol.NonTerminal -> lhs = lhs0.id
                    is Symbol.Terminal -> throw SyntaxErrorException(
                        "Expected rule lhs, found ${language[tokenId]}",
                        span
                    )
                }
                state = stateArrow
            }
            stateArrow -> {
                // Match arrow
                when (tokenId) {
                    arrowId -> {
                    }
                    metaEofId -> throw SyntaxErrorException("Expected arrow, found eof", span)
                    else -> throw SyntaxErrorException("Expected arrow, found terminal", span)
                }
                state = stateRhs
            }
            stateRhs -> {
                if (tokenId == newLineId || tokenId == metaEofId) {
                    // Rule end
                    rules.add(Rule(lhs, rhs.toTypedArray()))
                    lhs = -1
                    rhs.clear()

                    state = stateLhs
                    continue
                }
                rhs.add(backMap[tokenId]!!)
                // State unchanged
            }
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