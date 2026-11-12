package router;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class TaskProfiles {
    private final Map<String, Set<String>> profiles;

    public TaskProfiles(Map<String, Set<String>> profiles) {
        this.profiles = profiles.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    public void validate(RouteRequest request) {
        if (request == null) {
            throw new RouteFailure(RouteFailure.Code.INVALID_REQUEST);
        }
        request.validate();
        if (!profiles.getOrDefault(request.task(), Set.of()).contains(request.qualityProfile())) {
            throw new RouteFailure(RouteFailure.Code.UNKNOWN_TASK_PROFILE);
        }
    }
}
