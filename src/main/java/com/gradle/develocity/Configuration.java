package com.gradle.develocity;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static java.time.ZoneId.systemDefault;

final class Configuration {

    private static final String CONFIGURATION_FILE = "config.properties";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLL d uuuu kk:mm");

    private Configuration() {
    }

    static ConfigurationProperties load() {
        final var properties = new java.util.Properties();
        try (InputStream input = new FileInputStream(CONFIGURATION_FILE)) {
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from '%s' file".formatted(CONFIGURATION_FILE), e);
        }
        return new ConfigurationProperties(
                URI.create(properties.getProperty("serverUrl")),
                LocalDateTime.parse(properties.getProperty("since"), formatter).atZone(systemDefault()));
    }

    record ConfigurationProperties(URI serverUrl, ZonedDateTime since) {
    }

}
