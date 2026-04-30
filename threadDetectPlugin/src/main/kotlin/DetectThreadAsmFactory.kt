import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

private const val THREAD_OWNER = "java/lang/Thread"
private const val EXECUTOR_OWNER = "java/util/concurrent/ThreadPoolExecutor"
private const val EXECUTORS_OWNER = "java/util/concurrent/Executors"
private const val DETECTOR_OWNER = "com/iwatchme/android/detect/ThreadDetector"

abstract class DetectThreadAsmFactory : AsmClassVisitorFactory<DetectThreadAsmParams> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return ThreadDetectClassVisitor(Opcodes.ASM9, nextClassVisitor)
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        val name = classData.className
        if (name.startsWith("com.iwatchme.android.detect.ThreadDetector")) return false
        if (name.startsWith("com.iwatchme")) return true
        if (name.startsWith("kotlinx.coroutines.scheduling")) return true
        return false
    }
}

open class DetectThreadAsmParams : InstrumentationParameters

private class ThreadDetectClassVisitor(
    api: Int,
    classVisitor: ClassVisitor
) : ClassVisitor(api, classVisitor) {

    private var className: String = ""

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        className = name ?: ""
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        return DetectThreadMethodVisitor(api, mv, className)
    }
}

private class DetectThreadMethodVisitor(
    api: Int,
    methodVisitor: MethodVisitor,
    private val className: String
) : MethodVisitor(api, methodVisitor) {

    override fun visitMethodInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean
    ) {
        // Emit the original instruction first
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

        // Detect Thread.<init> (all constructor signatures)
        if (opcode == Opcodes.INVOKESPECIAL && owner == THREAD_OWNER && name == "<init>") {
            mv.visitLdcInsn(className)
            mv.visitLdcInsn(descriptor ?: "")
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                DETECTOR_OWNER,
                "onThreadInit",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                false
            )
            return
        }

        // Detect ThreadPoolExecutor.<init> (log only, no replacement)
        if (opcode == Opcodes.INVOKESPECIAL && owner == EXECUTOR_OWNER && name == "<init>") {
            mv.visitLdcInsn(className)
            mv.visitLdcInsn(descriptor ?: "")
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                DETECTOR_OWNER,
                "onExecutorInit",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                false
            )
            return
        }

        // Detect Executors.newXxx() factory methods
        if (opcode == Opcodes.INVOKESTATIC && owner == EXECUTORS_OWNER && name?.startsWith("new") == true) {
            mv.visitLdcInsn(className)
            mv.visitLdcInsn(name)
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                DETECTOR_OWNER,
                "onExecutorFactoryCall",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                false
            )
            return
        }
    }
}
