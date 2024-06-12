package com.gradle.develocity.core;

import com.gradle.develocity.api.Build;
import com.gradle.develocity.api.GradleArtifactTransformExecutions;
import com.gradle.develocity.api.GradleAttributes;
import com.gradle.develocity.api.GradleBuildCachePerformance;
import com.gradle.develocity.api.GradleDeprecations;
import com.gradle.develocity.api.GradleNetworkActivity;
import com.gradle.develocity.api.GradleProject;
import com.gradle.develocity.api.MavenAttributes;
import com.gradle.develocity.api.MavenBuildCachePerformance;
import com.gradle.develocity.api.MavenDependencyResolution;
import com.gradle.develocity.api.MavenModule;

import java.util.List;

public interface BuildConsumer {

    void onGradleBuild(GradleBuild build);

    void onMavenBuild(MavenBuild build);

    default void onFinish() {
    }

    record GradleBuild(
            Build build,
            GradleAttributes attributes,
            GradleBuildCachePerformance buildCachePerformance,
            GradleNetworkActivity networkActivity,
            List<GradleProject> projects,
            GradleDeprecations deprecations,
            GradleArtifactTransformExecutions artifactTransformExecutions) {
    }

    record MavenBuild(
            Build build,
            MavenAttributes attributes,
            MavenBuildCachePerformance performance,
            MavenDependencyResolution dependencyResolution,
            List<MavenModule> modules) {
    }

}
