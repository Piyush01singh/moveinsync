package com.moveinsync.mdm.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OsCompatibilityService {

    private static final Pattern RANGE_PATTERN =
            Pattern.compile("^\\s*([A-Za-z]+)\\s*(\\d+)(?:\\s*-\\s*(\\d+)|\\s*\\+)?\\s*$");
    private static final Pattern DEVICE_OS_PATTERN =
            Pattern.compile("^\\s*([A-Za-z]+)(?:\\s*(\\d+))?.*$");

    public boolean isSupportedRangeFormat(String supportedOsRange) {
        if (supportedOsRange == null || supportedOsRange.isBlank()) {
            return true;
        }
        return RANGE_PATTERN.matcher(supportedOsRange).matches();
    }

    public boolean isCompatible(String deviceOs, String supportedOsRange) {
        if (supportedOsRange == null || supportedOsRange.isBlank()) {
            return true;
        }

        ParsedRange range = parseRange(supportedOsRange);
        if (range == null) {
            return false;
        }

        ParsedDeviceOs parsedDeviceOs = parseDeviceOs(deviceOs);
        if (parsedDeviceOs == null) {
            return false;
        }

        if (!range.platform().equals(parsedDeviceOs.platform())) {
            return false;
        }

        int version = parsedDeviceOs.majorVersion();
        if (range.maxVersion() != null) {
            return version >= range.minVersion() && version <= range.maxVersion();
        }
        if (range.plusRange()) {
            return version >= range.minVersion();
        }
        return version == range.minVersion();
    }

    private ParsedRange parseRange(String supportedOsRange) {
        Matcher matcher = RANGE_PATTERN.matcher(supportedOsRange);
        if (!matcher.matches()) {
            return null;
        }
        String platform = normalizePlatform(matcher.group(1));
        int min = Integer.parseInt(matcher.group(2));
        String maxGroup = matcher.group(3);
        boolean plusRange = supportedOsRange.contains("+") && maxGroup == null;
        Integer max = maxGroup == null ? null : Integer.parseInt(maxGroup);
        return new ParsedRange(platform, min, max, plusRange);
    }

    private ParsedDeviceOs parseDeviceOs(String deviceOs) {
        if (deviceOs == null || deviceOs.isBlank()) {
            return null;
        }
        Matcher matcher = DEVICE_OS_PATTERN.matcher(deviceOs);
        if (!matcher.matches() || matcher.group(2) == null) {
            return null;
        }
        String platform = normalizePlatform(matcher.group(1));
        int majorVersion = Integer.parseInt(matcher.group(2));
        return new ParsedDeviceOs(platform, majorVersion);
    }

    private String normalizePlatform(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private record ParsedRange(String platform, int minVersion, Integer maxVersion, boolean plusRange) {
    }

    private record ParsedDeviceOs(String platform, int majorVersion) {
    }
}
