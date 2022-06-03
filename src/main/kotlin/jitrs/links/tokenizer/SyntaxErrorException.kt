package jitrs.links.tokenizer

import jitrs.util.TallException

class SyntaxErrorException(
    private val shortMessage: String,
    private var source: String? = null,
    private val span: Span
) : TallException() {
    override fun tallMessage(): String {
        val source1 = source as String

        val span2 = if (span.start < source1.length)
            span
        else
            Span(source1.length - 1, source1.length - 1)

        var line1Start: Int
        var line1End: Int

        val numberOfSpaces: Int
        val numberOfArrows: Int

        if (source1[span2.start] == '\n') {
            line1Start = source1.lastIndexOf('\n', span2.start - 1) + 1
            line1End = span2.start

            numberOfSpaces = span2.start - line1Start
            numberOfArrows = 1
        } else {
            line1Start = source1.lastIndexOf('\n', span2.start)
            if (line1Start == -1) line1Start = 0
            else line1Start++

            line1End = source1.indexOf('\n', span2.start + 1)
            if (line1End == -1) line1End = source1.length

            numberOfSpaces = (span2.start - line1Start).coerceAtLeast(0)
            numberOfArrows = (span2.end - span2.start).coerceAtMost(line1End - span2.start).coerceAtLeast(1)
        }

        val lineNo = source1.asSequence().take(line1Start).count { it == '\n' }
        val lineNoString = "$lineNo> "

        val line1 = source1.substring(line1Start, line1End)
        val line2 = " ".repeat(lineNoString.length + numberOfSpaces) + "^".repeat(numberOfArrows)

        return "$shortMessage\n" +
                "$lineNoString$line1\n" +
                line2
    }

    override fun shortMessage(): String = "$shortMessage at $span"

    fun setSource(source: String) =
        if (this.source == null)
            this.source = source
        else
            throw RuntimeException("Source already present")
}