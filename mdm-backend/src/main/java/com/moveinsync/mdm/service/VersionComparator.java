package com.moveinsync.mdm.service;

import org.springframework.stereotype.Component;

@Component
public class VersionComparator {

    public int compare(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }

        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int maxLength = Math.max(leftParts.length, rightParts.length);

        for (int index = 0; index < maxLength; index++) {
            int leftNumber = index < leftParts.length ? parsePart(leftParts[index]) : 0;
            int rightNumber = index < rightParts.length ? parsePart(rightParts[index]) : 0;

            if (leftNumber != rightNumber) {
                return Integer.compare(leftNumber, rightNumber);
            }
        }
        return 0;
    }

    public boolean isDowngrade(String currentVersion, String targetVersion) {
        return compare(currentVersion, targetVersion) > 0;
    }

    private int parsePart(String value) {
        String digits = value.replaceAll("[^0-9].*$", "");
        if (digits.isBlank()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }
}
