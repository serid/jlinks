package jitrs.links.tokenizer

class SyntaxErrorException(
    message: String,
    private val span: Span
) : Exception(message) {
    override val message: String
        get() = "${super.message} at $span"
}