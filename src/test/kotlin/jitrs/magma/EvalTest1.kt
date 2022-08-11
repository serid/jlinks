package jitrs.magma

import org.junit.jupiter.api.Test

internal class EvalTest1 : AbstractEvalTest() {
    @Test
    fun test1() {
        test("def id = fun A : Sort => fun x : A => x", "id int 1", "1")
    }

    @Test
    fun arithTest() {
        test("", "1 + 1", "2")
    }

    @Test
    fun factorialTest() {
        test("""def fact = fix fact x : int =>
            if x == 0
            then 1
            else x * fact (x - 1)
        """.trimIndent(), "fact 5", "120")
    }

    @Test
    fun fibonacciTest() {
        test("""def fib = fix fib x : int =>
            if x == 0
            then 0
            else if x == 1
            then 1
            else fib (x - 1) + fib (x - 2)
        """.trimIndent(), "fib 6", "8")
    }
}