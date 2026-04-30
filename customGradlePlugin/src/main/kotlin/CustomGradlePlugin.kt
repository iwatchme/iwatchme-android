import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.internal.plugins.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class CustomGradlePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension =
            target.extensions.create("customGradlePlugin", CustomGradlePluginExtension::class.java)

        target.plugins.withType(AppPlugin::class.java) {
            val androidComponents =
                target.extensions.findByType(AndroidComponentsExtension::class.java)
            androidComponents?.onVariants { variant ->
                if (extension.enableThreadDetect) {
                    variant.instrumentation.transformClassesWith(
                        DetectThreadAsmFactory::class.java,
                        InstrumentationScope.ALL
                    ) {}

                    variant.instrumentation.setAsmFramesComputationMode(
                        FramesComputationMode.COPY_FRAMES
                    )
                }
            }
        }
    }
}
