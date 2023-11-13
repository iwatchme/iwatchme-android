import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.tasks.ProcessJavaResTask
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

            val androidComponentExtension = target.extensions.findByType(AndroidComponentsExtension::class.java)
            androidComponentExtension?.onVariants {
                 variant ->
                  if (customGradlePluginExtension.enableThreadDetect) {
                      variant.instrumentation.transformClassesWith(
                          DetectThreadAsmFactory::class.java,
                          InstrumentationScope.ALL
                      ) {

                      }

                      variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
                  }

            }
        }
    }

}