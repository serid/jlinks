package jitrs.links

import jitrs.links.tablegen.SymbolArray
import jitrs.links.tokenizer.Scheme
import jitrs.links.tokenizer.tokenize
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class TokenizerTest {
    @Test
    fun tokenizerTest() {
        val scheme = Scheme(
            SymbolArray(
                arrayOf("<int>", "<ident>", "<string>", "<eof>"),
                arrayOf()
            )
        )

        val tokens = tokenize(
            scheme,
            "abc \"doeg\" 10",
            Character::isJavaIdentifierStart,
            Character::isJavaIdentifierPart
        )

        val actual = tokens.joinToString(",") { it.toString(scheme) }

        assertEquals("(<ident>:abc),(<string>:\"doeg\"),(<int>:10),<eof>", actual)
    }
}