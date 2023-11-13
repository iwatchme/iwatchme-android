import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

abstract class DetectThreadAsmFactory : AsmClassVisitorFactory<DetectThreadAsmParams> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return ThreadDetectClassVisitor(Opcodes.ASM8, nextClassVisitor)
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className.contains("com.iwatchme.jetpackstarter.MainActivity")
    }
}


open class DetectThreadAsmParams : InstrumentationParameters {

}


private class ThreadDetectClassVisitor(val api: Int, val classVisitor: ClassVisitor) :
    ClassVisitor(api, classVisitor) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val method = super.visitMethod(access, name, descriptor, signature, exceptions)
        return DetectThreadMethod(api, method)

    }


    private class DetectThreadMethod(val api: Int, val methodVisitor: MethodVisitor) :
        MethodVisitor(api, methodVisitor) {

        override fun visitMethodInsn(
            opcode: Int,
            owner: String?,
            name: String?,
            descriptor: String?,
            isInterface: Boolean
        ) {
            println("opcode: $opcode, owner: $owner, name: $name, descriptor: $descriptor")
            if (opcode == Opcodes.INVOKESPECIAL && owner == "java/lang/Thread") {
                println("Thread.start() is called")
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

    }
}


