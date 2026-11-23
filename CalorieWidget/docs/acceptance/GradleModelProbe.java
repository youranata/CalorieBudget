import java.io.File;
import java.util.*;
import org.gradle.tooling.*;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import com.android.builder.model.v2.models.*;
import com.android.builder.model.v2.ide.*;

public final class GradleModelProbe {
    public static final class Fetch implements BuildAction<String> {
        // Include the Kotlin enum runtime in the Tooling API action serialization classpath.
        private final Class<?> kotlinEnums = kotlin.enums.EnumEntries.class;
        public String execute(BuildController controller) {
            if (kotlinEnums == null) throw new IllegalStateException("Kotlin model runtime missing");
            BasicGradleProject root = controller.getBuildModel().getRootProject();
            BasicGradleProject app = root.getChildren().stream()
                .filter(p -> p.getPath().equals(":app")).findFirst().orElseThrow();
            Versions versions = controller.getModel(app, Versions.class);
            BasicAndroidProject basic = controller.getModel(app, BasicAndroidProject.class);
            AndroidDsl dsl = controller.getModel(app, AndroidDsl.class);
            ProjectSyncIssues issues = controller.getModel(app, ProjectSyncIssues.class);
            long errors = issues.getSyncIssues().stream().filter(i -> i.getSeverity() == SyncIssue.SEVERITY_ERROR).count();
            if (errors > 0) throw new IllegalStateException("Android model sync errors: " + issues.getSyncIssues());
            if (!basic.getMainSourceSet().getSourceProvider().getManifestFile().isFile())
                throw new IllegalStateException("Manifest missing from imported source set");
            if (basic.getBootClasspath().stream().noneMatch(f -> f.isFile() && f.getName().equals("android.jar")))
                throw new IllegalStateException("Android SDK boot classpath missing");
            return "root=" + root.getName() + "\nmodule=" + basic.getPath()
                + "\nprojectType=" + basic.getProjectType() + "\nagp=" + versions.getAgp()
                + "\ncompileTarget=" + dsl.getCompileTarget() + "\nbuildTools=" + dsl.getBuildToolsVersion()
                + "\napplicationId=" + dsl.getDefaultConfig().getApplicationId()
                + "\nminSdk=" + dsl.getDefaultConfig().getMinSdkVersion().getApiLevel()
                + "\ntargetSdk=" + dsl.getDefaultConfig().getTargetSdkVersion().getApiLevel()
                + "\nvariants=" + basic.getVariants().stream().map(BasicVariant::getName).sorted().toList()
                + "\nmanifest=" + basic.getMainSourceSet().getSourceProvider().getManifestFile()
                + "\nbootClasspath=" + basic.getBootClasspath()
                + "\nsyncErrors=" + errors + "\nsyncIssues=" + issues.getSyncIssues().size() + "\n";
        }
    }
    public static void main(String[] args) {
        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(new File(args[0]))
            .useInstallation(new File(args[1])).useGradleUserHomeDir(new File(args[2]));
        try (ProjectConnection connection = connector.connect()) {
            String model = connection.action(new Fetch())
                .withArguments("--offline", "--no-configuration-cache", "-Pandroid.injected.invoked.from.ide=true")
                .setStandardOutput(System.err).setStandardError(System.err).run();
            System.out.print(model);
        }
    }
}
