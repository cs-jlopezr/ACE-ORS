package utility;

import java.util.*;
import java.util.stream.Collectors;

public class SolutionTracker {

    // Core data structures
    private final Set<String> solutionSet;      // For O(1) duplicate detection
    private final List<Map<String, Integer>> solutionList;  // For ordered access
    private final Map<String, Integer> solutionFrequency;    // Track duplicates if they occur

    // Variable order for consistent string representation
    private static final List<String> VARIABLES = Arrays.asList(
            "x11", "x12", "x13", "x21", "x22", "x23", "x31", "x32", "x33"
    );

    // Statistics
    private int duplicateAttempts = 0;
    private int uniqueSolutionsFound = 0;

    public SolutionTracker() {
        this.solutionSet = new HashSet<>();
        this.solutionList = new ArrayList<>();
        this.solutionFrequency = new HashMap<>();
    }

    /**
     * Convert a solution to a unique string key.
     */
    private String toKey(Map<String, Integer> solution) {
        StringBuilder sb = new StringBuilder();
        for (String var : VARIABLES) {
            if (sb.length() > 0) sb.append(",");
            sb.append(var).append("=").append(solution.get(var));
        }
        return sb.toString();
    }

    /**
     * Convert a solution to a unique string key using varargs.
     * Example: toKey("x11",1,"x12",0,"x13",2,...)
     */
    private String toKey(String... varValuePairs) {
        Map<String, Integer> tempMap = new HashMap<>();
        for (int i = 0; i < varValuePairs.length; i += 2) {
            tempMap.put(varValuePairs[i], Integer.parseInt(varValuePairs[i+1]));
        }
        return toKey(tempMap);
    }

    /**
     * Register a solution and check if it's a duplicate.
     * Returns: true if solution was NEW (unique), false if DUPLICATE
     */
    public boolean registerSolution(Map<String, Integer> solution) {
        String key = toKey(solution);

        // Track frequency regardless
        solutionFrequency.put(key, solutionFrequency.getOrDefault(key, 0) + 1);

        // Check for duplicate
        if (solutionSet.contains(key)) {
            duplicateAttempts++;
            return false;  // Duplicate found
        }

        // New solution
        solutionSet.add(key);
        solutionList.add(new LinkedHashMap<>(solution));  // Store a copy
        uniqueSolutionsFound++;
        return true;  // New unique solution
    }

    /**
     * Register a solution using varargs format.
     * Example: registerSolution("x11",1,"x12",0,"x13",2,"x21",1,"x22",2,"x23",0,"x31",1,"x32",2,"x33",2)
     */
    public boolean registerSolution(String... varValuePairs) {
        Map<String, Integer> solution = new LinkedHashMap<>();
        for (int i = 0; i < varValuePairs.length; i += 2) {
            solution.put(varValuePairs[i], Integer.parseInt(varValuePairs[i+1]));
        }
        return registerSolution(solution);
    }

    /**
     * Check if a solution has been registered before (without registering it).
     */
    public boolean isDuplicate(Map<String, Integer> solution) {
        return solutionSet.contains(toKey(solution));
    }

    /**
     * Check if a solution has been registered before (varargs version).
     */
    public boolean isDuplicate(String... varValuePairs) {
        return solutionSet.contains(toKey(varValuePairs));
    }

    /**
     * Get how many times a specific solution has been attempted (including duplicates).
     */
    public int getAttemptCount(Map<String, Integer> solution) {
        return solutionFrequency.getOrDefault(toKey(solution), 0);
    }

    /**
     * Get all unique solutions found so far.
     */
    public List<Map<String, Integer>> getAllSolutions() {
        return new ArrayList<>(solutionList);
    }

    /**
     * Get the most recent solution found.
     */
    public Map<String, Integer> getLastSolution() {
        return solutionList.isEmpty() ? null : new LinkedHashMap<>(solutionList.get(solutionList.size() - 1));
    }

    /**
     * Get solution by index (order found).
     */
    public Map<String, Integer> getSolution(int index) {
        if (index < 0 || index >= solutionList.size()) return null;
        return new LinkedHashMap<>(solutionList.get(index));
    }

    /**
     * Get statistics about the search process.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("uniqueSolutions", uniqueSolutionsFound);
        stats.put("duplicateAttempts", duplicateAttempts);
        stats.put("totalAttempts", uniqueSolutionsFound + duplicateAttempts);
        stats.put("duplicateRate", (duplicateAttempts * 100.0) / (uniqueSolutionsFound + duplicateAttempts));
        return stats;
    }

    /**
     * Print a summary of what's been found.
     */
    public void printSummary() {
        System.out.println("=== Solution Tracker Summary ===");
        System.out.println("Unique solutions: " + uniqueSolutionsFound);
        System.out.println("Duplicate attempts: " + duplicateAttempts);
        System.out.println("Total attempts: " + (uniqueSolutionsFound + duplicateAttempts));
        System.out.printf("Duplicate rate: %.2f%%\n",
                (duplicateAttempts * 100.0) / (uniqueSolutionsFound + duplicateAttempts));
        System.out.println("================================");
    }

    /**
     * Print all solutions found (formatted nicely).
     */
    public void printAllSolutions() {
        System.out.println("\n=== All Unique Solutions Found ===");
        for (int i = 0; i < solutionList.size(); i++) {
            System.out.println("Solution " + (i+1) + ":");
            Map<String, Integer> sol = solutionList.get(i);
            System.out.println("    {");
            int varCount = 0;
            for (String var : VARIABLES) {
                String comma = (++varCount < VARIABLES.size()) ? "," : "";
                System.out.println("        " + var + ": " + sol.get(var) + comma);
            }
            System.out.println("    }");
        }
    }

    /**
     * Reset the tracker (start fresh).
     */
    public void reset() {
        solutionSet.clear();
        solutionList.clear();
        solutionFrequency.clear();
        duplicateAttempts = 0;
        uniqueSolutionsFound = 0;
    }

    /**
     * Check if a partial solution exists in any registered solution.
     * Useful for pruning search branches.
     */
    public boolean containsPartial(Map<String, Integer> partialSolution) {
        for (Map<String, Integer> fullSolution : solutionList) {
            boolean matches = true;
            for (Map.Entry<String, Integer> entry : partialSolution.entrySet()) {
                String var = entry.getKey();
                Integer partialValue = entry.getValue();
                Integer fullValue = fullSolution.get(var);

                if (!partialValue.equals(fullValue)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find all registered solutions that match a partial assignment.
     */
    public List<Map<String, Integer>> findMatchingPartial(Map<String, Integer> partialSolution) {
        List<Map<String, Integer>> matches = new ArrayList<>();
        for (Map<String, Integer> fullSolution : solutionList) {
            boolean matchesAll = true;
            for (Map.Entry<String, Integer> entry : partialSolution.entrySet()) {
                if (!entry.getValue().equals(fullSolution.get(entry.getKey()))) {
                    matchesAll = false;
                    break;
                }
            }
            if (matchesAll) {
                matches.add(fullSolution);
            }
        }
        return matches;
    }

    // Convenience method for creating partial maps
    public static Map<String, Integer> partial(String... varValuePairs) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < varValuePairs.length; i += 2) {
            map.put(varValuePairs[i], Integer.parseInt(varValuePairs[i+1]));
        }
        return map;
    }

}
