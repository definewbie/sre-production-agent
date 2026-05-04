package ai.sreagent.alertmanager.filter;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Filters parsed Alertmanager alerts based on request criteria.
 */
public class AlertmanagerAlertFilter {

    /**
     * Filter alerts by label matchers, firing/resolved state.
     *
     * @param alerts all parsed alerts
     * @param labelMatchers label key=value pairs to match
     * @param includeResolved whether to include resolved alerts
     * @param onlyFiring whether to only include firing alerts
     * @return filtered list
     */
    public List<AlertmanagerAlert> filter(List<AlertmanagerAlert> alerts,
                                           Map<String, String> labelMatchers,
                                           boolean includeResolved,
                                           boolean onlyFiring) {
        if (alerts == null || alerts.isEmpty()) {
            return List.of();
        }

        return alerts.stream()
                .filter(buildLabelMatcher(labelMatchers))
                .filter(buildStateFilter(includeResolved, onlyFiring))
                .collect(Collectors.toList());
    }

    private Predicate<AlertmanagerAlert> buildLabelMatcher(Map<String, String> labelMatchers) {
        if (labelMatchers == null || labelMatchers.isEmpty()) {
            return alert -> true;
        }
        return alert -> {
            for (Map.Entry<String, String> matcher : labelMatchers.entrySet()) {
                String alertValue = alert.labels().get(matcher.getKey());
                if (!matcher.getValue().equals(alertValue)) {
                    return false;
                }
            }
            return true;
        };
    }

    private Predicate<AlertmanagerAlert> buildStateFilter(boolean includeResolved, boolean onlyFiring) {
        return alert -> {
            if (onlyFiring && !alert.isFiring()) {
                return false;
            }
            if (!includeResolved && alert.isResolved()) {
                return false;
            }
            return true;
        };
    }
}
