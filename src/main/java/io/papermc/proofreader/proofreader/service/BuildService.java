package io.papermc.proofreader.proofreader.service;

import io.papermc.proofreader.proofreader.ProofReaderConfig.Config;
import io.papermc.proofreader.proofreader.util.FileUtil;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.papermc.proofreader.proofreader.service.StateService.State;

@Service
public class BuildService {

    private final StateService states;
    private final Config config;
    private final TaskExecutor taskExecutor;

    BuildService(StateService states, Config config, TaskExecutor taskScheduler) {
        this.states = states;
        this.config = config;
        this.taskExecutor = taskScheduler;
    }

    public void triggerBuild(State state) {
        state.status = "Starting Build";
        states.updateState(state);
        taskExecutor.execute(() -> build(state));
    }

    void build(State state) {
        try {
            state.status = "Cloning repository";
            states.updateState(state);

            ensureEmptyBuildDir(state);
            cloneRepo(state);

            state.status = "Running build";
            states.updateState(state);
            runBuild(state);

            // TODO
//            saveArtifacts(state);

            state.status = "Pushing source";
            states.updateState(state);
            pushSource(state);

            state.status = "Build completed successfully";
            state.branch = "pr/" + state.prNumber;
            states.updateState(state);
        } catch (Exception e) {
            state.status = "Build failed: " + e.getMessage();
            if (e.getCause() != null) {
                state.status += " | Cause: " + e.getCause().getMessage();
            }
            states.updateState(state);
            e.printStackTrace();
        }
    }

    private void pushSource(State state) {
        try {
            // move paper-server/src/minecraft to pr-x-minecraft
            var buildDir = Path.of(Objects.requireNonNull(state.buildDir));
            var ogMcDir = buildDir.resolve("paper-server").resolve("src").resolve("minecraft");
            var remoteDir = Path.of(Objects.requireNonNull(state.buildDir) + "-minecraft");
            FileUtil.moveDirectory(ogMcDir, remoteDir);

            // filter
            exec(remoteDir.resolve("java"), "Filtering java", "git", "filter-repo", "--to-subdirectory-filter", "paper-server/src/minecraft/java", "--force");
            exec(remoteDir.resolve("resources"), "Filtering resources", "git", "filter-repo", "--to-subdirectory-filter", "paper-server/src/minecraft/resources", "--force");

            // adding remotes
            exec(buildDir, "Adding java remote", "git", "remote", "add", "-f", "java", buildDir.relativize(remoteDir.resolve("java")).toString());
            exec(buildDir, "Adding resources remote", "git", "remote", "add", "-f", "resources", buildDir.relativize(remoteDir.resolve("resources")).toString());

            // merging
            exec(buildDir, "Merging java", "git", "merge", "--allow-unrelated-histories", "java/main");
            exec(buildDir, "Merging resources", "git", "merge", "--allow-unrelated-histories", "resources/main");

            // pushing
            exec(buildDir, "Adding proofreader remote", "git", "remote", "add", "proofreader", "https://github.com/" + config.targetRepo().owner() + "/" + config.targetRepo().name());
            exec(buildDir, "Pushing to proofreader", "git", "push", "proofreader", "-f");
        } catch (Exception e) {
            throw new RuntimeException("Pushing source failed", e);
        }
    }

    private void runBuild(State state) {
        try {
            System.out.println("Running build in " + state.buildDir);
            var executable = System.getProperty("os.name").toLowerCase().contains("win") ? "gradlew.bat" : "./gradlew";

            var pb = new ProcessBuilder()
                    .command(executable, "applyPatches")
                    .directory(Path.of(Objects.requireNonNull(state.buildDir)).toFile())
                    .inheritIO();
            pb.environment().putAll(buildGradleEnv());
            exec(pb, "Apply patches");

            pb = new ProcessBuilder()
                    .command(executable, "build", "createMojmapPaperclipJar", "generateDevelopmentBundle")
                    .directory(Path.of(Objects.requireNonNull(state.buildDir)).toFile())
                    .inheritIO();
            pb.environment().putAll(buildGradleEnv());
            exec(pb, "Gradle build");
        } catch (Exception e) {
            throw new RuntimeException("Build process failed", e);
        }
    }

    private void cloneRepo(State state) {
        try {
            System.out.println("Cloning repo into " + state.buildDir);
            exec(state, "Git init", "git", "init");
            exec(state, "Git fetch", "git", "fetch", "https://github.com/" + config.sourceRepo().owner() + "/" + config.sourceRepo().name() + ".git", "pull/" + state.prNumber + "/head:pr/" + state.prNumber);
            exec(state, "Git switch", "git", "switch", "pr/" + state.prNumber);
        } catch (Exception e) {
            throw new RuntimeException("Git clone failed", e);
        }
    }

    private void exec(ProcessBuilder pb, String thing) throws Exception {
        var result = pb.start().waitFor();
        if (result != 0) {
            throw new RuntimeException(thing + " failed with exit code " + result);
        }
    }

    private void exec(State state, String thing, String... command) throws Exception {
        exec(Path.of(Objects.requireNonNull(state.buildDir)), thing, command);
    }

    private void exec(Path dir, String thing, String... command) throws Exception {
        System.out.println("Executing " + String.join(" ", command) + " in " + dir);
        var pb = new ProcessBuilder()
                .command(command)
                .directory(dir.toFile())
                .inheritIO();
        exec(pb, thing);
    }

    private void ensureEmptyBuildDir(State state) {
        if (state.buildDir == null) {
            state.buildDir = "work/builds/pr-" + state.prNumber;
        }
        var dir = Path.of(state.buildDir);
        if (Files.exists(dir)) {
            FileUtil.deleteRecursively(dir);
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create build dir", e);
        }
    }

    private Map<String, String> buildGradleEnv() {
        var env = new HashMap<String, String>();
        env.put("CI", "true");

        if (StringUtils.hasText(config.buildCachePassword()) && StringUtils.hasText(config.buildCacheUser())) {
            env.put("ORG_GRADLE_PROJECT_paperBuildCacheEnabled", "true");
            env.put("ORG_GRADLE_PROJECT_paperBuildCacheUsername", config.buildCacheUser());
            env.put("ORG_GRADLE_PROJECT_paperBuildCachePassword", config.buildCachePassword());
            env.put("ORG_GRADLE_PROJECT_paperBuildCachePush", "true");
        }

        return env;
    }

    public void triggerRebase(State state) {
        // TODO trigger rebase
        state.status = "Starting rebase";

        states.updateState(state);
    }
}
