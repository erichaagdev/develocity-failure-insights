package com.gradle.develocity.core;

import java.time.Duration;

final class Durations {

    static String format(Duration duration) {
        if (duration.abs().getSeconds() == 0) {
            return "0s";
        }

        long days = duration.abs().toDaysPart();
        long hours = duration.abs().toHoursPart();
        long minutes = duration.abs().toMinutesPart();
        long seconds = duration.abs().toSecondsPart();

        StringBuilder s = new StringBuilder();
        if (duration.isNegative()) {
            s.append('-');
        }
        if (days != 0) {
            s.append(days).append("d ");
        }
        if (hours != 0) {
            s.append(hours).append("h ");
        }
        if (minutes != 0) {
            s.append(minutes).append("m ");
        }
        if (seconds != 0) {
            s.append(seconds).append("s");
        }
        return s.toString().trim();
    }

}
