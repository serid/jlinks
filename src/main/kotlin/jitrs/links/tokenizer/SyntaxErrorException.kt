package jitrs.links.tokenizer

import jitrs.util.TallException

class SyntaxErrorException(
    private val shortMessage: String,
    private var source: String? = null,
    private val span: Span
) : TallException() {
    override fun tallMessage(): String {
        val source1 = source as String

        var line1Start = source1.lastIndexOf('\n', span.start)
        if (line1Start == -1) line1Start = source1.length
        else line1Start++

        var line1End = source1.indexOf('\n', span.start + 1)
        if (line1End == -1) line1End = source1.length

        val numberOfSpaces = (span.start - line1Start).coerceAtLeast(0)
        val numberOfArrows = (span.end - span.start).coerceAtMost(line1End - span.start).coerceAtLeast(1)

        val line1 = source1.substring(line1Start, line1End)
        val line2 = " ".repeat(numberOfSpaces) + "^".repeat(numberOfArrows)

        return "$shortMessage\n" +
                "$line1\n" +
                line2
    }

    override fun shortMessage(): String = "$shortMessage at $span"

    fun setSource(source: String) =
        if (this.source == null)
            this.source = source
        else
            throw RuntimeException("Source already present")
}