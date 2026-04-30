import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes


const val O_ThreadPoolExecutor = "java/util/concurrent/ThreadPoolExecutor"
const val O_BaseProxyThreadPoolExecutor = "com/iwatchme/android/ProxyThreadExecutor2"

abstract class DetectThreadAsmFactory : AsmClassVisitorFactory<DetectThreadAsmParams> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return ThreadDetectClassVisitor(Opcodes.ASM6, nextClassVisitor)
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className.contains("com.iwatchme.android")
                || classData.className.contains("java.util.concurrent")
    }
}


open class DetectThreadAsmParams : InstrumentationParameters {

}


private class ThreadDetectClassVisitor(val api: Int, val classVisitor: ClassVisitor) :
    ClassVisitor(api, classVisitor) {

    var className: String = ""


    override fun visitSource(source: String?, debug: String?) {
        super.visitSource(source, debug)
        //println("visitSource: ${source} ${debug}")
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        // println("visit: ${version} ${access} ${name} ${signature} ${superName} ${interfaces}")
        className = name ?: ""
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitInnerClass(
        name: String?,
        outerName: String?,
        innerName: String?,
        access: Int
    ) {
        // println("visitInnerClass: ${name} ${outerName} ${innerName} ${access}")
        super.visitInnerClass(name, outerName, innerName, access)

    }

    override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {
        // println("visitOutClass: ${owner} ${name} ${descriptor}")
        super.visitOuterClass(owner, name, descriptor)
    }


    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        // println("out: ${access} ${name} ${descriptor} ${signature} ${exceptions}")
        val method = super.visitMethod(access, name, descriptor, signature, exceptions)
        return DetectThreadMethod(api, method, className)
    }


    private class DetectThreadMethod(
        api: Int,
        methodVisitor: MethodVisitor,
        val className: String
    ) :
        MethodVisitor(api, methodVisitor) {

        override fun visitMethodInsn(
            opcode: Int,
            owner: String?,
            name: String?,
            descriptor: String?,
            isInterface: Boolean
        ) {
            // println("in: ${opcode} ${owner} ${name} ${descriptor} ${isInterface}")
            if (opcode == Opcodes.INVOKESPECIAL && owner == "java/lang/Thread" && name == "<init>" && descriptor == "(Ljava/lang/Runnable;)V") {
                mv.visitLdcInsn("$className");
                mv.visitMethodInsn(
                    opcode,
                    owner,
                    name,
                    "(Ljava/lang/Runnable;Ljava/lang/String;)V",
                    isInterface
                )
                return
            } else if (owner.equals(O_ThreadPoolExecutor) && name == "<init>") {
                println("owner: $owner name: $name descriptor: $descriptor")
                when (descriptor) {
                    "(IIJLjava/util/concurrent/TimeUnit;Ljava/util/concurrent/BlockingQueue;)V" -> {
                        mv.visitLdcInsn(className);
                        mv.visitMethodInsn(
                            opcode,
                            O_BaseProxyThreadPoolExecutor,
                            name,
                            "(IIJLjava/util/concurrent/TimeUnit;Ljava/util/concurrent/BlockingQueue;Ljava/lang/String;)V",
                            false
                        );
                    }

                    "(IIJLjava/util/concurrent/TimeUnit;Ljava/util/concurrent/BlockingQueue;Ljava/util/concurrent/ThreadFactory;)V" -> {
                        mv.visitLdcInsn(className);
                        mv.visitMethodInsn(
                            opcode,
                            O_BaseProxyThreadPoolExecutor,
                            name,
                            "(IIJLjava/util/concurrent/TimeUnit;Ljava/util/concurrent/BlockingQueue;Ljava/util/concurrent/ThreadFactory;Ljava/lang/String;)V",
                            false
                        );
                    }

                    "(IIJLjava/util/concurrent/TimeUnit;Ljava/util/concurrent/BlockingQueue;Ljava/util/concurrent/RejectedExecutionHandler;)V" -> {
                        mv.visitLdcInsn(className);
                        mv.visitMethodInsn(
                            opcode,
                            O_BaseProxyThreadPoolExecutor,
                            name,
                            "(IIJLjava/util/concurrent/TimeUnit;Ljava/util/concurrent/BlockingQueue;Ljava/util/concurrent/RejectedExecutionHandler;Ljava/lang/String;)V",
                            false
                        );
                    }

                    "(IIJLjava/util/concurrent/TimeUnit;Ljava/util/concurrent/BlockingQueue;Ljava/util/concurrent/ThreadFactory;Ljava/util/concurrent/RejectedExecutionHandler;)V" -> {
                        mv.visitLdcInsn(className);
                        mv.visitMethodInsn(
                            opcode,
                            O_BaseProxyThreadPoolExecutor,
                            name,
                            "(IIJLjava/util/concurrent/TimeUnit;Ljava/util/concurrent/BlockingQueue;Ljava/util/concurrent/ThreadFactory;Ljava/util/concurrent/RejectedExecutionHandler;Ljava/lang/String;)V",
                            false
                        );
                    }

                    else -> {
                        mv.visitMethodInsn(
                            opcode,
                            O_BaseProxyThreadPoolExecutor,
                            name,
                            descriptor,
                            false
                        );
                    }
                }
                return;
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

    }
}


