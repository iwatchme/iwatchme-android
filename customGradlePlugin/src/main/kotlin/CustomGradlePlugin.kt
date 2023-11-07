import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.register
import java.util.concurrent.atomic.AtomicBoolean

class CustomGradlePlugin : Plugin<Project> {

    private val androidAppPluginApplied = AtomicBoolean(false);

    override fun apply(target: Project) {
        androidAppPluginApplied.set(false)
        val customGradlePluginExtension =
            target.extensions.create("customGradlePlugin", CustomGradlePluginExtension::class.java)

        target.plugins.withType(AppPlugin::class.java) {
            println("${this}")
            androidAppPluginApplied.set(true)

            val androidExtension = target.extensions.findByType(AppExtension::class.java)
            androidExtension?.applicationVariants?.configureEach{
                if (customGradlePluginExtension.enableThreadDetect) {
                    val appVariant = this

                   val threadTaskProvider = target.tasks.register<ThreadDetectTask>(
                        "threadDetectTaskFor${appVariant.name.capitalized()}"
                    ) {

                    }

                    val compileTask = target.tasks.getByName("compile${}JavaWithJavac")

                    threadTaskProvider.dependsOn(compileTask)

                }
            }


        }
    }

}