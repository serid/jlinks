package jitrs.magma.backend

@Suppress("ClassName")
class ByteClassLoader : ClassLoader() {
    fun defineClass1(name: String, b: ByteArray, off: Int, len: Int): Class<*> = defineClass(name, b, off, len)

    fun resolveClass1(c: Class<*>) = resolveClass(c)
}