package com.gradle.develocity.core;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.time.Duration.between;
import static java.time.ZoneId.systemDefault;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparing;
import static java.util.Map.Entry.comparingByKey;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.iterate;

public final class IncidentReport {

    private final ZonedDateTime since;
    private final ZonedDateTime until;
    private final Resolution resolution;
    private final List<Incident> incidents;
    private final Map<ZonedDateTime, List<Incident>> incidentsPartitioned;

    private String overall;
    private String ciOverall;
    private String localOverall;
    private String ciPerProjectRequested;
    private String localPerUser;
    private String localPerProject;
    private String localPerUserProject;

    private String overallTrends;
    private String ciOverallTrends;
    private String localOverallTrends;
    private String ciPerProjectRequestedTrends;
    private String localPerUserTrends;
    private String localPerProjectTrends;
    private String localPerUserProjectTrends;

    public IncidentReport(IncidentTracker tracker, ZonedDateTime since, ZonedDateTime until) {
        this.since = since;
        this.until = until;
        this.resolution = Resolution.from(between(since, until));
        this.incidents = sortChronologically(tracker.getResolvedIncidents());
        this.incidentsPartitioned = partition(tracker.getResolvedIncidents());
        initializeOverall();
        initializeCiOverall();
        initializeLocalOverall();
        initializeCiPerProjectRequested();
        initializeLocalPerUser();
        initializeLocalPerProject();
        initializeLocalPerUserProject();
    }

    private void initializeOverall() {
        final var title = "CI & Local, Overall";
        this.overall = computeOverall(title, allBuilds());
        this.overallTrends = computeOverallTrends(title, allBuilds());
    }

    private void initializeCiOverall() {
        final var title = "CI, Overall";
        this.ciOverall = computeOverall(title, onlyCiBuilds());
        this.ciOverallTrends = computeOverallTrends(title, onlyCiBuilds());
    }

    private void initializeLocalOverall() {
        final var title = "Local, Overall";
        this.localOverall = computeOverall(title, onlyLocalBuilds());
        this.localOverallTrends = computeOverallTrends(title, onlyLocalBuilds());
    }

    private void initializeCiPerProjectRequested() {
        record Key(String projectName, Collection<String> requested) { }
        final var title = "CI, By Project & Requested tasks/goals";
        final var headers = List.of("Project", "Requested tasks/goals");
        final var groupingBy = groupBy(it -> new Key(it.projectName(), it.requested()));
        final var keyExtractor = extractKey(Key.class, it -> Stream.of(it.projectName(), String.join(" ", it.requested())));
        this.ciPerProjectRequested = computeGroupedBy(title, headers, onlyCiBuilds(), groupingBy, keyExtractor);
        this.ciPerProjectRequestedTrends = computeGroupedByTrends(title, headers, onlyCiBuilds(), groupingBy, keyExtractor);
    }

    private void initializeLocalPerUser() {
        record Key(String username) { }
        final var title = "Local, By User";
        final var headers = List.of("User");
        final var groupingBy = groupBy(it -> new Key(it.username()));
        final var keyExtractor = extractKey(Key.class, it -> Stream.of(it.username()));
        this.localPerUser = computeGroupedBy(title, headers, onlyLocalBuilds(), groupingBy, keyExtractor);
        this.localPerUserTrends = computeGroupedByTrends(title, headers, onlyLocalBuilds(), groupingBy, keyExtractor);
    }

    private void initializeLocalPerProject() {
        record Key(String projectName) { }
        final var title = "Local, By Project";
        final var headers = List.of("Project");
        final var groupingBy = groupBy(it -> new Key(it.projectName()));
        final var keyExtractor = extractKey(Key.class, it -> Stream.of(it.projectName()));
        this.localPerProject = computeGroupedBy(title, headers, onlyLocalBuilds(), groupingBy, keyExtractor);
        this.localPerProjectTrends = computeGroupedByTrends(title, headers, onlyLocalBuilds(), groupingBy, keyExtractor);
    }

    private void initializeLocalPerUserProject() {
        record Key(String username, String projectName) { }
        final var title = "Local, By User & Project";
        final var headers = List.of("User", "Project");
        final var groupingBy = groupBy(it -> new Key(it.username(), it.projectName()));
        final var keyExtractor = extractKey(Key.class, it -> Stream.of(it.username(), it.projectName()));
        this.localPerUserProject = computeGroupedBy(title, headers, onlyLocalBuilds(), groupingBy, keyExtractor);
        this.localPerUserProjectTrends = computeGroupedByTrends(title, headers, onlyLocalBuilds(), groupingBy, keyExtractor);
    }

    public String overall() {
        return overall;
    }

    public String ciOverall() {
        return ciOverall;
    }

    public String localOverall() {
        return localOverall;
    }

    public String ciPerProjectRequested() {
        return ciPerProjectRequested;
    }

    public String localPerUser() {
        return localPerUser;
    }

    public String localPerProject() {
        return localPerProject;
    }

    public String localPerUserProject() {
        return localPerUserProject;
    }

    public String overallTrends() {
        return overallTrends;
    }

    public String ciOverallTrends() {
        return ciOverallTrends;
    }

    public String localOverallTrends() {
        return localOverallTrends;
    }

    public String ciPerProjectRequestedTrends() {
        return ciPerProjectRequestedTrends;
    }

    public String localPerUserTrends() {
        return localPerUserTrends;
    }

    public String localPerProjectTrends() {
        return localPerProjectTrends;
    }

    public String localPerUserProjectTrends() {
        return localPerUserProjectTrends;
    }

    private String computeOverall(
            String title,
            Predicate<Incident> filter
    ) {
        final var row = calculateStatistics(incidents.stream().filter(filter).toList());
        final var table = Table.withTitle("Time To Remediate Build Failures (" + title + ")");
        final var defaultHeaders = List.of("Failures", "Mean", "Median", "Min", "Max", "P5", "P25", "P75", "P95");
        table.header(defaultHeaders.toArray());
        addRow(table, row);
        return table.toString();
    }

    private <Key> String computeGroupedBy(
            String title,
            List<String> headers,
            Predicate<Incident> filter,
            Function<Incident, Key> groupingBy,
            Function<Key, Stream<String>> keyExtractor
    ) {
        final var rows = incidents
                .stream()
                .filter(filter)
                .collect(groupingBy(groupingBy))
                .entrySet()
                .stream()
                .collect(toMap(Entry::getKey, it -> calculateStatistics(it.getValue())))
                .entrySet()
                .stream()
                .sorted(reverseOrder(comparing(it -> it.getValue().getN())))
                .toList();
        final var table = Table.withTitle("Time To Remediate Build Failures (" + title + ")");
        final var defaultHeaders = List.of("Failures", "Mean", "Median", "Min", "Max", "P5", "P25", "P75", "P95");
        table.header(concat(headers.stream(), defaultHeaders.stream()).toArray());
        rows.forEach(s -> addRow(table, s.getValue(), keyExtractor.apply(s.getKey()).toArray()));
        return table.toString();
    }

    private String computeOverallTrends(
            String title,
            Predicate<Incident> filter) {
        final var row = incidentsPartitioned
                .entrySet()
                .stream()
                .collect(toMap(Entry::getKey, it -> calculateStatistics(it.getValue().stream().filter(filter).toList())))
                .entrySet()
                .stream()
                .sorted(comparingByKey())
                .toList();
        final var table = Table.withTitle("Time To Remediate Build Failures (" + title + ")");
        table.header(concat(Stream.of("Failures"), row.stream().map(Entry::getKey).map(resolution::format)).toArray());
        final var failures = (int) row.stream().mapToDouble(value -> value.getValue().getN()).sum();
        table.row(concat(Stream.of(failures), row.stream().map(it -> format(it.getValue().getMean()))).toArray());
        return table.toString();
    }

    private <Key> String computeGroupedByTrends(
            String title,
            List<String> headers,
            Predicate<Incident> filter,
            Function<Incident, Key> groupingBy,
            Function<Key, Stream<String>> keyExtractor) {
        record Row<Key>(Key key, int failures, Map<ZonedDateTime, DescriptiveStatistics> columns) { }
        final var ticks = incidentsPartitioned.keySet().stream().sorted().toList();
        final var rows = transpose(incidentsPartitioned
                .entrySet()
                .stream()
                .collect(groupingByKeyAndCalculatingStatistics(filter, groupingBy)))
                .entrySet()
                .stream()
                .map(it -> new Row<>(it.getKey(), countFailures(it), it.getValue()))
                .sorted(reverseOrder(comparing(Row::failures)))
                .toList();
        final var table = Table.withTitle("Time To Remediate Build Failures (" + title + ")");
        table.header(concat(concat(headers.stream(), Stream.of("Failures")), ticks.stream().map(resolution::format)).toArray());
        rows.forEach(row -> table.row(concat(concat(
                keyExtractor.apply(row.key()),
                Stream.of(row.failures)),
                ticks.stream().map(it -> row.columns().containsKey(it) ? format(row.columns().get(it).getMean()) : "--")).toArray()));
        return table.toString();
    }

    private static <Key> int countFailures(Entry<Key, Map<ZonedDateTime, DescriptiveStatistics>> row) {
        return (int) row.getValue().values().stream().mapToDouble(DescriptiveStatistics::getN).sum();
    }

    private List<Incident> sortChronologically(List<Incident> incidents) {
        return incidents.stream().sorted(comparing(Incident::startedOn)).toList();
    }

    private Map<ZonedDateTime, List<Incident>> partition(List<Incident> incidents) {
        final var ticks = iterate(resolution.truncate(since), it -> it.compareTo(until) < 0, it -> it.plus(1, resolution.asChronoUnit())).toList();
        final var partitionedIncidents = new HashMap<>(incidents
                .stream()
                .collect(groupingBy(it -> resolution.truncate(it.startedOn().atZone(systemDefault())))));
        ticks.forEach(it -> partitionedIncidents.computeIfAbsent(it, __ -> emptyList()));
        return partitionedIncidents;
    }

    private static DescriptiveStatistics calculateStatistics(List<Incident> incidents) {
        var s = new DescriptiveStatistics();
        incidents.stream().mapToLong(it -> it.duration().toMillis()).forEach(s::addValue);
        return s;
    }

    private static void addRow(Table table, DescriptiveStatistics s, Object... keys) {
        final var contents = concat(
                stream(keys),
                Stream.of(
                        s.getN(),
                        format(s.getMean()),
                        format(s.getPercentile(50)),
                        format(s.getMin()),
                        format(s.getMax()),
                        format(s.getPercentile(5)),
                        format(s.getPercentile(25)),
                        format(s.getPercentile(75)),
                        format(s.getPercentile(95))
                )).toArray();
        table.row(contents);
    }

    private static <T> Map<T, Map<ZonedDateTime, DescriptiveStatistics>> transpose(Map<ZonedDateTime, Map<T, DescriptiveStatistics>> rows) {
        record Entry(ZonedDateTime outer, Object inner, DescriptiveStatistics value) {}
        //noinspection unchecked
        return (Map<T, Map<ZonedDateTime, DescriptiveStatistics>>) rows
                .entrySet()
                .stream()
                .flatMap(outer -> outer.getValue().entrySet().stream().map(inner -> new Entry(outer.getKey(), inner.getKey(), inner.getValue())))
                .collect(groupingBy(it -> it.inner, toMap(it -> it.outer, it -> it.value)));
    }

    private static <T> Collector<Entry<ZonedDateTime, List<Incident>>, ?, Map<ZonedDateTime, Map<T, DescriptiveStatistics>>> groupingByKeyAndCalculatingStatistics(
            Predicate<Incident> filter,
            Function<Incident, T> groupingBy) {
        return toMap(Entry::getKey, partition -> partition
                .getValue()
                .stream()
                .filter(filter)
                .collect(groupingBy(groupingBy))
                .entrySet()
                .stream()
                .collect(toMap(Entry::getKey, it -> calculateStatistics(it.getValue()))));
    }

    private static Predicate<Incident> allBuilds() {
        return it -> true;
    }

    private static Predicate<Incident> onlyCiBuilds() {
        return Incident::isCI;
    }

    private static Predicate<Incident> onlyLocalBuilds() {
        return not(Incident::isCI);
    }

    private static <Key> Function<Incident, Key> groupBy(Function<Incident, Key> groupingBy) {
        return groupingBy;
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
    private static <Key> Function<Key, Stream<String>> extractKey(Class<Key> type, Function<Key, Stream<String>> keyExtractor) {
        return keyExtractor;
    }

    private static String format(double millis) {
        return Durations.format(Duration.ofMillis((long) millis));
    }

}
