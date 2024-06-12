package com.gradle.develocity.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

public class AccessKeyProvider {

    private static final String ACCESS_KEY = "DEVELOCITY_ACCESS_KEY";
    private static final String LEGACY_ACCESS_KEY = "GRADLE_ENTERPRISE_ACCESS_KEY";
    private static final String GRADLE_USER_HOME = "GRADLE_USER_HOME";

    private static final String MALFORMED_ENVIRONMENT_VARIABLE_ERROR = "Environment variable %s"
            + " is malformed (expected format: 'server-host=access-key' or 'server-host1=access-key1;server-host2=access-key2')";

    private AccessKeyProvider() {
    }

    public static Optional<String> lookupAccessKey(URI serverUrl) {
        try {
            Properties accessKeysByHost = new Properties();
            accessKeysByHost.putAll(loadMavenHomeAccessKeys("gradle-enterprise"));
            accessKeysByHost.putAll(loadMavenHomeAccessKeys("develocity"));
            accessKeysByHost.putAll(loadGradleHomeAccessKeys("enterprise"));
            accessKeysByHost.putAll(loadGradleHomeAccessKeys("develocity"));
            accessKeysByHost.putAll(loadFromEnvVar(LEGACY_ACCESS_KEY));
            accessKeysByHost.putAll(loadFromEnvVar(ACCESS_KEY));

            return Optional.ofNullable(accessKeysByHost.getProperty(serverUrl.getHost()));
        } catch (IOException e) {
            System.out.println("Error while trying to read access keys: " + e.getMessage() + ". Will try fetching build scan data without authentication.");
            return Optional.empty();
        }
    }

    private static Properties loadGradleHomeAccessKeys(String baseDir) throws IOException {
        Path accessKeysFile = getGradleUserHomeDirectory().resolve(baseDir + "/keys.properties");
        return loadAccessKeysFromFile(accessKeysFile);
    }

    private static Path getGradleUserHomeDirectory() {
        if (isNullOrEmpty(System.getenv(GRADLE_USER_HOME))) {
            return Paths.get(System.getProperty("user.home"), ".gradle");
        }
        return Paths.get(System.getenv(GRADLE_USER_HOME));
    }

    private static Properties loadMavenHomeAccessKeys(String dir) throws IOException {
        Path accessKeysFile = getMavenStorageDirectory(dir).resolve("keys.properties");
        return loadAccessKeysFromFile(accessKeysFile);
    }

    private static Path getMavenStorageDirectory(String dir) {
        String defaultLocation = System.getProperty("user.home") + "/.m2/." + dir;
        return Paths.get(System.getProperty("develocity.storage.directory", System.getProperty("gradle.enterprise.storage.directory", defaultLocation)));
    }

    private static Properties loadAccessKeysFromFile(Path accessKeysFile) throws IOException {
        Properties accessKeysByHost = new Properties();
        if (Files.isRegularFile(accessKeysFile)) {
            try (BufferedReader in = Files.newBufferedReader(accessKeysFile)) {
                accessKeysByHost.load(in);
            }
        }
        return accessKeysByHost;
    }

    private static Properties loadFromEnvVar(String envVar) {
        Properties accessKeys = new Properties();
        String value = System.getenv(envVar);

        if (isNullOrEmpty(value)) {
            return accessKeys;
        }

        String[] entries = value.split(";");
        for (String entry : entries) {
            if (entry == null) {
                throw new RuntimeException(malformedEnvVarError(envVar));
            }

            String[] parts = entry.split("=", 2);
            if (parts.length < 2) {
                throw new RuntimeException(malformedEnvVarError(envVar));
            }

            String joinedServers = parts[0].trim();
            String accessKey = parts[1].trim();

            if (joinedServers.isEmpty() || isNullOrEmpty(accessKey)) {
                throw new RuntimeException(malformedEnvVarError(envVar));
            }
            for (String server : joinedServers.split(",")) {
                server = server.trim();
                if (server.isEmpty()) {
                    throw new RuntimeException(malformedEnvVarError(envVar));
                }
                accessKeys.put(server, accessKey);
            }
        }

        return accessKeys;
    }

    private static String malformedEnvVarError(String envVar) {
        return String.format(MALFORMED_ENVIRONMENT_VARIABLE_ERROR, envVar);
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

}
