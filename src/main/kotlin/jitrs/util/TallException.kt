package jitrs.util

abstract class TallException : Exception() {
    abstract fun shortMessage(): String

    abstract fun tallMessage(): String

    override val message: String
        get() = tallMessage()

    override fun getLocalizedMessage(): String = shortMessage()
}

class SimpleTallException(
    private val shortMessage: String,
    private val tallMessage: String,
) : TallException() {
    override fun shortMessage(): String = shortMessage

    override fun tallMessage(): String = tallMessage
}