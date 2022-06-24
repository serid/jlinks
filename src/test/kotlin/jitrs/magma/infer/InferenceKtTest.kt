package jitrs.magma.infer

import jitrs.datastructures.DisjointSetObject
import jitrs.magma.Compiler
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class InferenceKtTest {
    private lateinit var compiler: Compiler

    @BeforeAll
    fun init() {
        compiler = Compiler.new()
    }

    @Test
    fun inferTest() {
        val k = compiler.getIr("fun x => fun y => x")
        val typeK = compiler.infer(k)

        val expectedK = PolyType(
            2,
            DisjointSetObject.new(
                MonoType.newArrow(
                    DisjointSetObject.new(MonoType.Var(0)),
                    DisjointSetObject.new(
                        MonoType.newArrow(
                            DisjointSetObject.new(MonoType.Var(1)),
                            DisjointSetObject.new(MonoType.Var(0))
                        )
                    )
                )
            )
        )

        assertEquals(expectedK, typeK)
    }

    @Test
    fun inferTest2() {
        val ir = compiler.getIr("fun x => fun y => fun z => if x then y else z")

        println(ir.prettyString())

        val type = compiler.infer(ir)

        val expected = PolyType(
            1,
            DisjointSetObject.new(
                MonoType.newArrow(
                    Types.intType,
                    DisjointSetObject.new(
                        MonoType.newArrow(
                            DisjointSetObject.new(MonoType.Var(0)),
                            DisjointSetObject.new(
                                MonoType.newArrow(
                                    DisjointSetObject.new(MonoType.Var(0)),
                                    DisjointSetObject.new(MonoType.Var(0))
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(expected, type)
    }

    @Test
    fun inferTest3() {
        val ir = compiler.getIr("fun o => let x = fun s => o in x")

        println(ir.prettyString())

        val type = compiler.infer(ir)

        val expected = PolyType(
            2,
            DisjointSetObject.new(
                MonoType.newArrow(
                    DisjointSetObject.new(MonoType.Var(0)),
                    DisjointSetObject.new(
                        MonoType.newArrow(
                            DisjointSetObject.new(MonoType.Var(1)),
                            DisjointSetObject.new(MonoType.Var(0))
                        )
                    )
                )
            )
        )

        assertEquals(expected, type)
    }

    @Test
    fun occursCheckTest() {
        val ir = compiler.getIr("fun x => x x")

        assertThrows<OccursCheckException> {
            compiler.infer(ir)
        }
    }
}