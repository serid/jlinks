package jitrs.links.parser

import jitrs.links.Cst
import jitrs.links.tablegen.Rules
import jitrs.links.tablegen.Symbol
import jitrs.links.tokenizer.Scheme
import java.lang.reflect.Constructor

fun getContainingClassOrPackageName(clazz: Class<*>): String = clazz.name.removeSuffix(clazz.simpleName)

class CstToAst private constructor(
    private val scheme: Scheme,

    private val reflectionInfo: ReflectionInfo
) {
    // TODO: This block of code can be invoked straight from parser bypassing Cst stage.
    // Not sure if such implementation would be faster
    fun cstToAst(cst: Cst.Node): AutoAst {
        val args = cst.children.asSequence()
            // Filter out terminals without data
            .filter { child -> child !is Cst.Leaf || scheme.specialIdInfo.isTerminalWithData(child.token.id) }
            // Map children
            .map { child ->
                when (child) {
                    is Cst.Leaf -> child.token.data
                    is Cst.Node -> cstToAst(child)
                }
            }.toList().toTypedArray()

        val constructor = reflectionInfo[cst.ruleId]
        return constructor.newInstance(*args) as AutoAst
    }

    companion object {
        fun new(
            scheme: Scheme,
            rules: Rules,
            containerName: String,
        ): CstToAst {
            // Find class for each NonTerminalId
            val nonTerminalClasses0 = Array<Class<*>?>(scheme.map.nonTerminals.size) { null }
            for ((id, name) in scheme.map.nonTerminals.withIndex()) {
                nonTerminalClasses0[id] = Class.forName("$containerName$name")
            }
            val nonTerminalClasses = nonTerminalClasses0.requireNoNulls()

            // For each nonterminal, keep count of how many times it was encountered as lhs in rule list
            val counters = Array(scheme.map.nonTerminals.size) { 1 }
            val reductionInfo = Array<ReductionInfo?>(rules.size) { null }
            for ((ruleId, rule) in rules.withIndex()) {
                // Find class for lhs
                val name = scheme.map.nonTerminals[rule.lhs]
                val variantName = name + counters[rule.lhs]
                val clazz = Class.forName("$containerName$name$$variantName")

                // Find fields
                val fields = ArrayList<Class<*>>()
                for (symbol in rule.rhs) {
                    when (symbol) {
                        is Symbol.Terminal -> {
                            if (scheme.specialIdInfo.isTerminalWithData(symbol.id))
                                when (symbol.id) {
                                    scheme.specialIdInfo.intSpecialId -> fields.add(Int::class.java)
                                    scheme.specialIdInfo.identSpecialId -> fields.add(String::class.java)
                                    scheme.specialIdInfo.stringSpecialId -> fields.add(String::class.java)
                                }
                        }
                        is Symbol.NonTerminal -> {
                            fields.add(nonTerminalClasses[symbol.id])
                        }
                    }
                }

                reductionInfo[ruleId] = clazz.getConstructor(*fields.toTypedArray())
                counters[rule.lhs]++
            }

            val reflectionInfo = reductionInfo.requireNoNulls()

            return CstToAst(scheme, reflectionInfo)
        }
    }
}

// key is RuleId
typealias ReflectionInfo = Array<ReductionInfo>

/**
 * Information to perform a reduction
 */
typealias ReductionInfo = Constructor<*>

/**
 * Abstract class for trees used in parsing
 */
abstract class AutoAst