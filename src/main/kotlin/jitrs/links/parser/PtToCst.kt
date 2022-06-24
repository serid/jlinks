package jitrs.links.parser

import jitrs.links.Pt
import jitrs.links.RuleConstructorMap
import jitrs.links.tablegen.Rules
import jitrs.links.tablegen.Symbol
import jitrs.links.tokenizer.Scheme
import java.lang.reflect.Constructor

fun getContainingClassOrPackageName(clazz: Class<*>): String = clazz.name.removeSuffix(clazz.simpleName)

class PtToCst private constructor(
    private val scheme: Scheme,

    private val reflectionInfo: ReflectionInfo
) {
    // TODO: This block of code can be invoked straight from parser bypassing ParseTree stage.
    // Not sure if such implementation would be faster
    fun convert(pt: Pt.Node): AutoCst {
        val args = pt.children.asSequence()
            // Filter out terminals without data
            .filter { child -> child !is Pt.Leaf || scheme.specialIdInfo.isTerminalWithData(child.token.id) }
            // Map children
            .map { child ->
                when (child) {
                    is Pt.Leaf -> child.token.data
                    is Pt.Node -> convert(child)
                }
            }.toList().toTypedArray()

        val constructor = reflectionInfo[pt.ruleId]
        return constructor.newInstance(*args) as AutoCst
    }

    companion object {
        fun new(
            scheme: Scheme,
            rules: Rules,
            ruleConstructorMap: RuleConstructorMap,
            containerName: String,
        ): PtToCst {
            // Find class for each NonTerminalId
            val nonTerminalClasses0 = Array<Class<*>?>(scheme.map.nonTerminals.size) { null }
            for ((id, name) in scheme.map.nonTerminals.withIndex()) {
                nonTerminalClasses0[id] = Class.forName("$containerName$name")
            }
            val nonTerminalClasses = nonTerminalClasses0.requireNoNulls()

            val reductionInfo = Array<ReductionInfo?>(rules.size) { null }
            for ((ruleId, rule) in rules.withIndex()) {
                // Find class for lhs
                val name = scheme.map.nonTerminals[rule.lhs]
                val variantName = ruleConstructorMap[ruleId]
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
            }

            val reflectionInfo = reductionInfo.requireNoNulls()

            return PtToCst(scheme, reflectionInfo)
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
abstract class AutoCst