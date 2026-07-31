package utility;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class RobustProfileManager {

    private final Set<String> criticalEntries = new HashSet<>();
    private final Map<String, Integer> shiftToId = new HashMap<>();

    public RobustProfileManager(String profilePath, Map<String, Integer> shiftMapping) {
        this.shiftToId.putAll(shiftMapping);
        loadProfile(profilePath);
    }

    private void loadProfile(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                System.err.println("[RobustProfileManager] Profile not found: " + path);
                return;
            }

            // Simple JSON parsing using manual regex-based approach for the known format.
            // Assuming the JSON looks like: [{"day": 2, "shift": "D1"}, ...]
            String content = new String(Files.readAllBytes(Paths.get(path)));
            
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{\\s*\"day\"\\s*:\\s*(\\d+)\\s*,\\s*\"shift\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");
            java.util.regex.Matcher m = p.matcher(content);
            while (m.find()) {
                int day = Integer.parseInt(m.group(1));
                String shift = m.group(2);
                criticalEntries.add(day + ":" + shift);
            }
            System.out.println("[RobustProfileManager] Loaded " + criticalEntries.size() + " critical entries.");
        } catch (Exception e) {
            System.err.println("[RobustProfileManager] Error loading profile: " + e.getMessage());
        }
    }

    public boolean isCritical(int day, String shiftId) {
        return criticalEntries.contains(day + ":" + shiftId);
    }

    public boolean isCritical(int day, int shiftIdx) {
        // Find the shift ID for this index
        for (Map.Entry<String, Integer> entry : shiftToId.entrySet()) {
            if (entry.getValue() == shiftIdx) {
                return isCritical(day, entry.getKey());
            }
        }
        return false;
    }

    public boolean requiresRobustness(String varId) {
        // Assumes variable IDs are like "x[day][person]" or "x[day,person]"
        if (!varId.startsWith("x[")) {
            return false;
        }
        try {
            int start = varId.indexOf("[");
            int end = varId.indexOf("]", start);
            if (start != -1 && end != -1) {
                String indexPart = varId.substring(start + 1, end);
                String[] parts = indexPart.split("[,]");
                int day = Integer.parseInt(parts[0].trim());
                
                for (String entry : criticalEntries) {
                    if (entry.startsWith(day + ":")) return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }
    
    public int getDay(String varId) {
        try {
            int start = varId.indexOf("[");
            int end = varId.indexOf("]", start);
            if (start != -1 && end != -1) {
                String indexPart = varId.substring(start + 1, end);
                String[] parts = indexPart.split("[,]");
                return Integer.parseInt(parts[0].trim());
            }
        } catch (Exception e) {}
        return -1;
    }
}
