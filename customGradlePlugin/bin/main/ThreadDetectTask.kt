import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class ThreadDetectTask : DefaultTask() {

    @TaskAction
    fun doSomething() {
        println("Hello from CustomGradlePluginTask")
    }

}