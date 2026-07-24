package com.aivle26.aipm.Service;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Comparator;

@Component
public class ArtifactVersionComparator implements Comparator<String> {

    @Override
    public int compare(String left, String right) {
        String[] leftSegments = segments(left);
        String[] rightSegments = segments(right);
        int length = Math.max(leftSegments.length, rightSegments.length);

        for (int index = 0; index < length; index++) {
            BigInteger leftValue = index < leftSegments.length
                    ? new BigInteger(leftSegments[index])
                    : BigInteger.ZERO;
            BigInteger rightValue = index < rightSegments.length
                    ? new BigInteger(rightSegments[index])
                    : BigInteger.ZERO;
            int comparison = leftValue.compareTo(rightValue);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private String[] segments(String version) {
        if (version == null || !version.trim().matches("\\d+(\\.\\d+)*")) {
            throw new IllegalArgumentException("invalid artifact version");
        }
        return version.trim().split("\\.");
    }
}
