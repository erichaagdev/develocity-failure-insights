package com.gradle.develocity;

import com.gradle.develocity.api.DevelocityClient;
import com.gradle.develocity.core.IncidentReport;
import com.gradle.develocity.core.IncidentTracker;

import java.time.ZonedDateTime;
import java.util.Set;

import static com.gradle.develocity.api.BuildModel.GRADLE_ATTRIBUTES;
import static com.gradle.develocity.api.BuildModel.MAVEN_ATTRIBUTES;
import static com.gradle.develocity.core.BuildProcessor.processBuilds;

final class Main {

    public static void main(String[] args) {
        final var configuration = Configuration.load();
        final var develocity = new DevelocityClient(configuration.serverUrl());
        final var incidentTracker = new IncidentTracker();

        System.out.printf("Processing builds from %s%n", configuration.serverUrl());
        processBuilds(
                develocity,
                null,
                configuration.since(),
                Set.of(GRADLE_ATTRIBUTES, MAVEN_ATTRIBUTES),
                Set.of(incidentTracker));

        final var now = ZonedDateTime.now();
        final var incidentReport = new IncidentReport(incidentTracker, configuration.since(), now);
        println(incidentReport.overall());
        println(incidentReport.ciOverall());
        println(incidentReport.localOverall());
        println(incidentReport.ciPerProjectRequested());
        println(incidentReport.localPerUser());
        println(incidentReport.localPerProject());
        println(incidentReport.localPerUserProject());
        println(incidentReport.overallTrends());
        println(incidentReport.ciOverallTrends());
        println(incidentReport.localOverallTrends());
        println(incidentReport.ciPerProjectRequestedTrends());
        println(incidentReport.localPerUserTrends());
        println(incidentReport.localPerProjectTrends());
        println(incidentReport.localPerUserProjectTrends());
    }

    private static void println(String value) {
        System.out.println(value);
    }

}
