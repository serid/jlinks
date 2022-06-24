package jitrs.links

import jitrs.links.tablegen.*
import jitrs.links.tokenizer.*

/**
 * Parse a string into an array of parsing rules
 */
fun metaParse(scheme: Scheme, string: String, parseConstructorEh: Boolean): Pair<Rules, RuleConstructorMap?> {
    // Build a language with terminals required for parsing rules
    // In this language what was nonterminals is treated as terminals
    val terminalNonTerminals = scheme.map.nonTerminals.asSequence()
    val language = scheme.map.terminals.asSequence()
        .withIndex()
        .map { (id, str) -> escapeSpecialTerminal(scheme.specialIdInfo, id, str) }
        .plus(sequenceOf(".", "->", "\n", "<ident>", "<eof>"))
        .plus(terminalNonTerminals)
        .toList().toTypedArray()

    Scheme.sortTerminals(language)

    val dotId: TerminalId = language.indexOf(".")
    val arrowId: TerminalId = language.indexOf("->")
    val newLineId: TerminalId = language.indexOf("\n")
    val metaIdentId: TerminalId = language.indexOf("<ident>")
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

    val tokens = tokenize(
        language,
        SpecialIdInfo.from(language),
        string,
        ::isConstructorStartAndPart,
        ::isConstructorStartAndPart
    )

    // State machine for parsing rules
    val stateLhsName = 0 // Reading lhs name
    val stateLhsDot = 1 // Reading lhs dot
    val stateLhsConstructor = 2 // Reading lhs constructor
    val stateArrow = 3 // Reading arrow
    val stateRhs = 4 // Reading rhs

    var state = stateLhsName

    val rules = ArrayList<Rule>()
    var lhsId: NonTerminalId = -1
    // Key is RuleId
    val ruleConstructorMap = ArrayList<String?>()
    val rhs = ArrayList<Symbol>()
    for ((tokenId, data, span) in tokens) {
        when (state) {
            stateLhsName -> {
                if (tokenId == newLineId || tokenId == metaEofId) continue
                when (val lhs0 = backMap[tokenId]) {
                    is Symbol.NonTerminal -> lhsId = lhs0.id
                    is Symbol.Terminal ->
                        throw SyntaxErrorException("Expected rule lhs, found ${language[tokenId]}", string, span)
                }
                state = stateLhsDot
            }
            stateLhsDot -> {
                state = when (tokenId) {
                    // If token is arrow, skip to rhs
                    arrowId -> {
                        ruleConstructorMap.add(null)
                        stateRhs
                    }
                    dotId -> stateLhsConstructor
                    else -> throw SyntaxErrorException("Expected \".\", found ${language[tokenId]}", string, span)
                }
            }
            stateLhsConstructor -> {
                if (tokenId != metaIdentId)
                    throw SyntaxErrorException("Expected <ident>, found ${language[tokenId]}", string, span)

                val ruleId = rules.size

                ruleConstructorMap.add(data as String)

                state = stateArrow
            }
            stateArrow -> {
                // Match arrow
                if (tokenId != arrowId)
                    throw SyntaxErrorException("Expected arrow, found ${language[tokenId]}", string, span)
                state = stateRhs
            }
            stateRhs -> {
                if (tokenId == newLineId || tokenId == metaEofId) {
                    // Rule end
                    rules.add(Rule(lhsId, rhs.toTypedArray()))
                    lhsId = -1
                    rhs.clear()

                    state = stateLhsName
                    continue
                }
                rhs.add(backMap[tokenId]!!)
                // State unchanged
            }
        }
    }

    return if (parseConstructorEh) {
        val nullConstructors = ruleConstructorMap.asSequence()
            .withIndex()
            .filter { (_, x) -> x == null }
            .map { (i, _) -> i }
            .toList()
        if (nullConstructors.isNotEmpty())
            throw RuntimeException("Constructor names were requested but not provided for rules: $nullConstructors")
        Pair(rules.toTypedArray(), ruleConstructorMap.requireNoNulls().toTypedArray())
    } else
        Pair(rules.toTypedArray(), null)
}

fun isConstructorStartAndPart(c: Char): Boolean = c.isLetter() || c.isDigit()

// Key is RuleId
typealias RuleConstructorMap = Array<String>