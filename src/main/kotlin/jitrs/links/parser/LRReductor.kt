package jitrs.links.parser

import jitrs.links.RuleConstructorMap
import jitrs.links.tablegen.NonTerminalId
import jitrs.links.tablegen.RuleId
import jitrs.links.tablegen.Rules
import jitrs.links.tablegen.Symbol
import jitrs.links.tokenizer.Scheme
import jitrs.links.tokenizer.Token
import jitrs.util.cast
import java.lang.reflect.Constructor

interface LRReductor {
    fun onReduce(id: NonTerminalId, ruleId: RuleId, children: Array<StackItem>): AutoCst
}

class LRToCst(
    scheme: Scheme,
    rules: Rules,
    ruleConstructorMap: RuleConstructorMap,
    containerName: String,
) : LRReductor {
    private val reflectionInfo: ReflectionInfo = getReflectionInfo(scheme, rules, ruleConstructorMap, containerName)

    override fun onReduce(id: NonTerminalId, ruleId: RuleId, children: Array<StackItem>): AutoCst {
        val args = children.asSequence()
            // Filter out terminals without data
            .filterNot { child -> child is StackItem.Shifted && child.token.data is Unit }
            // Map children
            .map { child ->
                when (child) {
                    is StackItem.Shifted -> child.token.data
                    is StackItem.Reduced -> child.cst
                }
            }.toList().toTypedArray()

        val constructor = reflectionInfo[ruleId]
        return constructor.newInstance(*args) as AutoCst
    }
}

class LRToString(
    val scheme: Scheme,
) : LRReductor {
    override fun onReduce(id: NonTerminalId, ruleId: RuleId, children: Array<StackItem>): AutoCst {
        val s = scheme.map.nonTerminals[id]
        return StringCst(children.asSequence()
            .joinToString(",", "$s:$ruleId[", "]") { child ->
            when (child) {
                is StackItem.Shifted -> child.token.toString(scheme)
                is StackItem.Reduced -> child.cst.cast<StringCst>().string
            }
        })
    }
}

data class StringCst(val string: String) : AutoCst()

private fun getReflectionInfo(
    scheme: Scheme,
    rules: Rules,
    ruleConstructorMap: RuleConstructorMap,
    containerName: String,
): ReflectionInfo {
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

    return reductionInfo.requireNoNulls()
}

fun getContainingClassOrPackageName(clazz: Class<*>): String = clazz.name.removeSuffix(clazz.simpleName)

// key is RuleId
typealias ReflectionInfo = Array<ReductionInfo>

/**
 * Information to perform a reduction
 */
typealias ReductionInfo = Constructor<*>

/**
 * Sum type for items on the LR parser stack
 */
sealed class StackItem {
    data class Shifted(val token: Token) : StackItem()

    data class Reduced(val cst: AutoCst) : StackItem()
}

/**
 * Abstract class for parse trees that can be constructed using reflection
 */
abstract class AutoCst