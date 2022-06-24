package jitrs.magma.backend

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class Backend {
    companion object {
        fun test() {
            val b = f()

            val cl = ByteClassLoader()
            val clazz = cl.defineClass1("emit.Foo", b, 0, b.size)
            cl.resolveClass1(clazz)

            clazz.getDeclaredMethod("main").invoke(null)
        }

        fun f(): ByteArray {
            val cw = ClassWriter(0)
            cw.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "emit/Foo", null, "java/lang/Object", null)
            cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "main", "()V", null, null).run {
                visitCode()
                visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                visitLdcInsn("Hi")
                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(2, 1)
                visitEnd()
            }
            cw.visitEnd()
            return cw.toByteArray()
        }
    }
}