package com.gradle.develocity.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.gradle.develocity.api.Build;
import com.gradle.develocity.api.BuildModel;
import com.gradle.develocity.api.DevelocityClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public final class BuildProcessor {

    private static final int discoveryMaxBuildsPerRequest = 1_000;
    private static final int progressInterval = 10_000;
    private static final String cacheDirectoryName = ".develocity-failure-insights";
    private static final ObjectMapper objectMapper = new JsonMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLL d uuuu HH:mm z");

    public static void processBuilds(
            DevelocityClient develocity,
            String query,
            ZonedDateTime since,
            int maxBuildsPerRequest,
            Set<BuildModel> buildModels,
            Collection<BuildConsumer> buildConsumers) {
        System.out.printf("Discovering builds since %s%n", since.format(formatter));
        final var builds = discoverBuilds(develocity, query, since);
        System.out.printf("Discovered %s builds%n", builds.size());
        System.out.printf("Processing builds%n");
        final var processingStartedOn = System.currentTimeMillis();
        var intervalStartedOn = System.currentTimeMillis();
        var lastUncachedBuildId = "";
        var lastCachedBuildId = "";
        var uncached = 0;
        var processed = 0;
        var fetched = 0;
        for (Build build : builds) {
            final var now = System.currentTimeMillis();
            if (now - intervalStartedOn >= progressInterval) {
                intervalStartedOn = System.currentTimeMillis();
                printProgress(now, processingStartedOn, processed, fetched, builds.size());
            }
            final var cachedBuildFile = getCachedBuildFile(build.getId());
            if (uncached == maxBuildsPerRequest || (cachedBuildFile.exists() && uncached > 0)) {
                processUncachedBuilds(develocity, query, buildModels, buildConsumers, lastCachedBuildId, uncached);
                lastCachedBuildId = lastUncachedBuildId;
                processed += uncached;
                fetched += uncached;
                uncached = 0;
            }
            if (cachedBuildFile.exists()) {
                processCachedBuild(develocity, buildModels, buildConsumers, cachedBuildFile);
                lastCachedBuildId = build.getId();
                processed++;
            } else {
                lastUncachedBuildId = build.getId();
                uncached++;
            }
        }
        if (uncached > 0) {
            processUncachedBuilds(develocity, query, buildModels, buildConsumers, lastCachedBuildId, uncached);
            processed += uncached;
            fetched += uncached;
        }
        System.out.println("100% complete, " + fetched + " builds fetched from API, " + processed + " total builds processed");
        buildConsumers.forEach(BuildConsumer::onFinish);
    }

    private static ArrayList<Build> discoverBuilds(DevelocityClient develocity, String query, ZonedDateTime since) {
        final var sinceMilli = since.toInstant().toEpochMilli();
        final var builds = new ArrayList<Build>();
        var fromBuild = "";
        do {
            final var response = develocity.getBuilds(query, discoveryMaxBuildsPerRequest, null, fromBuild);
            if (response.isEmpty()) return builds;
            if (response.getLast().getAvailableAt() < sinceMilli) {
                builds.addAll(response.stream().filter(it -> it.getAvailableAt() >= sinceMilli).toList());
                return builds;
            }
            builds.addAll(response);
            fromBuild = response.getLast().getId();
        } while (!builds.isEmpty());
        return builds;
    }

    private static void printProgress(long now, long processingStartedOn, int processed, int fetched, int total) {
        final var fetchingRatePerSecond = fetched / ((now - processingStartedOn) / 1000.0);
        final var estimatedTimeRemaining = Duration.ofSeconds((long) ((total - processed) / fetchingRatePerSecond));
        System.out.printf("%s%% complete, %s remaining%n", (processed * 100) / total, Durations.format(estimatedTimeRemaining));
    }

    private static void processCachedBuild(DevelocityClient develocity, Set<BuildModel> buildModels, Collection<BuildConsumer> buildConsumers, File cachedBuildFile) {
        final var cachedBuild = readCachedBuildFile(cachedBuildFile);
        if (cachedBuild.buildModels().equals(buildModels)) {
            buildConsumers.forEach(buildConsumer -> processBuild(cachedBuild.build(), buildConsumer));
            return;
        }
        final var build = develocity.getBuild(cachedBuild.build().getId(), buildModels);
        writeCachedBuildFile(cachedBuildFile, new CachedBuild(buildModels, build));
        buildConsumers.forEach(buildConsumer -> processBuild(cachedBuild.build(), buildConsumer));
    }

    private static void processUncachedBuilds(DevelocityClient develocity, String query, Set<BuildModel> buildModels, Collection<BuildConsumer> buildConsumers, String fromBuild, int maxBuilds) {
        final var builds = develocity.getBuilds(query, maxBuilds, buildModels, fromBuild);
        builds.forEach(build -> {
            final var cachedBuildFile = getCachedBuildFile(build.getId());
            writeCachedBuildFile(cachedBuildFile, new CachedBuild(buildModels, build));
            buildConsumers.forEach(buildConsumer -> processBuild(build, buildConsumer));
        });
    }

    @SuppressWarnings({"DataFlowIssue", "Convert2MethodRef"})
    private static void processBuild(Build build, BuildConsumer buildConsumer) {
        if (build.getBuildToolType().equals("gradle")) {
            buildConsumer.onGradleBuild(
                    new BuildConsumer.GradleBuild(
                            build,
                            ifNotNull(build.getModels().getGradleAttributes(), it -> it.getModel()),
                            ifNotNull(build.getModels().getGradleBuildCachePerformance(), it -> it.getModel()),
                            ifNotNull(build.getModels().getGradleNetworkActivity(), it -> it.getModel()),
                            ifNotNull(build.getModels().getGradleProjects(), it -> it.getModel()),
                            ifNotNull(build.getModels().getGradleDeprecations(), it -> it.getModel()),
                            ifNotNull(build.getModels().getGradleArtifactTransformExecutions(), it -> it.getModel())));
        } else if (build.getBuildToolType().equals("maven")) {
            buildConsumer.onMavenBuild(
                    new BuildConsumer.MavenBuild(
                            build,
                            ifNotNull(build.getModels().getMavenAttributes(), it -> it.getModel()),
                            ifNotNull(build.getModels().getMavenBuildCachePerformance(), it -> it.getModel()),
                            ifNotNull(build.getModels().getMavenDependencyResolution(), it -> it.getModel()),
                            ifNotNull(build.getModels().getMavenModules(), it -> it.getModel())));
        }
    }

    private static File getCachedBuildFile(String id) {
        return Path.of(System.getProperty("user.home"))
                .resolve(cacheDirectoryName)
                .resolve(id.substring(0, 2))
                .resolve(id + ".json")
                .toFile();
    }

    private static CachedBuild readCachedBuildFile(File cachedBuildFile) {
        try {
            return objectMapper.readValue(cachedBuildFile, CachedBuild.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeCachedBuildFile(File cachedBuildFile, CachedBuild cachedBuild) {
        try {
            //noinspection ResultOfMethodCallIgnored
            cachedBuildFile.getParentFile().mkdirs();
            Files.write(cachedBuildFile.toPath(), objectMapper.writeValueAsBytes(cachedBuild));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record CachedBuild(Set<BuildModel> buildModels, Build build) {
    }

    private static <T, R> R ifNotNull(T t, Function<T, R> consumer) {
        if (t != null) {
            return consumer.apply(t);
        }
        return null;
    }
}
