package jitrs.links

import jitrs.links.tablegen.*
import jitrs.links.tokenizer.*

/**
 * Parse a string into an array of parsing rules
 */
fun metaParse(scheme: Scheme, string: String, parseConstructorEh: Boolean): Pair<Rules, RuleConstructorMap?> {
    // <string> is used for escaped tokens like . and ->
    val language = arrayOf(".", "->", "<nl>", "<ident>", "<string>", "<eof>")

    val specialIdInfo = SpecialIdInfo.from(language)

    val dotId: TerminalId = language.indexOf(".")
    val arrowId: TerminalId = language.indexOf("->")
    val newLineId: TerminalId = specialIdInfo.newlineSpecialId
    val metaIdentId: TerminalId = specialIdInfo.identSpecialId
    val metaStringId: TerminalId = specialIdInfo.stringSpecialId
    val metaEofId: TerminalId = specialIdInfo.eofSpecialId

    val tokens0 = tokenize(
        language,
        specialIdInfo,
        string,
        { it != '.' && !it.isWhitespace() }
    )
    // Unquote arrows
    val tokens = tokens0.map { if (it.data == "\"->\"") it.modifyData("->") else it }

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
    @Suppress("LiftReturnOrAssignment")
    for ((tokenId, data, span) in tokens) when (state) {
        stateLhsName -> {
            if (tokenId == newLineId || tokenId == metaEofId) continue
            if (tokenId != metaIdentId)
                throw SyntaxErrorException("Expected rule lhs, found ${language[tokenId]}", string, span)

            // TODO: maybe hashmap would be faster than indexOf
            lhsId = scheme.map.nonTerminals.indexOf(data)
            if (lhsId == -1)
                throw SyntaxErrorException("$data nonterminal is not in list", string, span)

            state = stateLhsDot
        }

        stateLhsDot -> when (tokenId) {
            // If token is arrow, skip to rhs
            arrowId -> {
                ruleConstructorMap.add(null)
                state = stateRhs
            }

            dotId -> state = stateLhsConstructor
            else -> throw SyntaxErrorException("Expected \".\", found ${language[tokenId]}", string, span)
        }

        stateLhsConstructor -> {
            if (tokenId != metaIdentId)
                throw SyntaxErrorException("Expected <ident>, found ${language[tokenId]}", string, span)

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
            if (!(tokenId == metaIdentId || tokenId == metaStringId))
                throw SyntaxErrorException("Expected <ident> or <string>, found ${language[tokenId]}", string, span)

            // State unchanged
            val symbol = scheme.reverse[data] ?: throw SyntaxErrorException(
                "\"$data\" terminal or nonterminal is not in list",
                string,
                span
            )
            rhs.add(symbol)
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