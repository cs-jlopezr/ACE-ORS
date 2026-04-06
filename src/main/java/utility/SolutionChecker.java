package utility;

import java.util.*;
import java.util.stream.Collectors;

import java.util.*;
import java.util.stream.Collectors;

public class SolutionChecker {

    // Store all possible solutions (ground truth)
    private final List<Map<String, Integer>> allSolutions;
    private final Set<String> allSolutionStrings;

    // Track which solutions have been found during search
    private final Set<String> foundSolutionsSet;
    private final List<Map<String, Integer>> foundSolutionsList;

    // Variable names in order
    private static final List<String> VARIABLES = Arrays.asList(
            "x11", "x12", "x13", "x21", "x22", "x23", "x31", "x32", "x33"
    );

    public SolutionChecker() {
        this.allSolutions = new ArrayList<>();
        this.allSolutionStrings = new HashSet<>();
        this.foundSolutionsSet = new HashSet<>();
        this.foundSolutionsList = new ArrayList<>();
        loadAllSolutions();
    }

    private void loadAllSolutions() {
        // All 107 solutions extracted from the log file
        int[][] solutionValues = {
                // run1 solutions (4 solutions)
                {0,0,2,1,2,0,1,2,2},
                {0,0,2,1,2,1,1,2,1},
                {0,0,2,1,2,2,1,2,0},
                {0,1,1,1,1,1,1,2,2},

                // run2 solutions (8 solutions - actually from run1 to run2 transition)
                {0,1,1,1,1,2,1,2,1},
                {0,1,1,1,2,1,1,1,2},
                {0,1,1,1,2,2,1,1,1},
                {0,1,2,1,1,0,1,2,2},

                // run3 solutions (23 solutions - accumulating)
                {0,1,2,1,1,1,1,2,1},
                {0,1,2,1,1,2,1,2,0},
                {0,1,2,1,2,0,1,1,2},
                {0,1,2,1,2,1,1,1,1},
                {0,1,2,1,2,2,1,1,0},
                {1,0,1,0,2,1,1,2,2},
                {1,0,1,0,2,2,1,2,1},
                {1,0,1,1,1,1,1,2,2},
                {1,0,1,1,1,2,1,2,1},
                {1,0,1,1,2,0,1,2,2},
                {1,0,1,1,2,1,0,2,2},
                {1,0,1,1,2,1,1,1,2},
                {1,0,1,1,2,1,1,2,1},
                {1,0,1,1,2,1,1,2,2},
                {1,0,1,1,2,2,0,2,1},

                // run4 solutions (57 solutions - accumulating)
                {1,0,1,1,2,2,1,1,1},
                {1,0,1,1,2,2,1,2,0},
                {1,0,1,1,2,2,1,2,1},
                {1,0,2,0,2,0,1,2,2},
                {1,0,2,0,2,1,1,2,1},
                {1,0,2,0,2,2,1,2,0},
                {1,0,2,1,1,0,1,2,2},
                {1,0,2,1,1,1,1,2,1},
                {1,0,2,1,1,2,1,2,0},
                {1,0,2,1,2,0,0,2,2},
                {1,0,2,1,2,0,1,1,2},
                {1,0,2,1,2,0,1,2,1},
                {1,0,2,1,2,0,1,2,2},
                {1,0,2,1,2,1,0,2,1},
                {1,0,2,1,2,1,1,1,1},
                {1,0,2,1,2,1,1,2,0},
                {1,0,2,1,2,1,1,2,1},
                {1,0,2,1,2,2,0,2,0},
                {1,0,2,1,2,2,1,1,0},
                {1,0,2,1,2,2,1,2,0},
                {1,1,0,0,1,2,1,2,2},
                {1,1,0,0,2,2,1,1,2},
                {1,1,0,1,0,2,1,2,2},
                {1,1,0,1,1,1,1,2,2},
                {1,1,0,1,1,2,0,2,2},
                {1,1,0,1,1,2,1,1,2},
                {1,1,0,1,1,2,1,2,1},
                {1,1,0,1,1,2,1,2,2},
                {1,1,0,1,2,1,1,1,2},
                {1,1,0,1,2,2,0,1,2},
                {1,1,0,1,2,2,1,0,2},
                {1,1,0,1,2,2,1,1,1},
                {1,1,0,1,2,2,1,1,2},
                {1,1,1,0,1,1,1,2,2},

                // run5 solutions (83 solutions - accumulating)
                {1,1,1,0,1,2,1,2,1},
                {1,1,1,0,2,1,1,1,2},
                {1,1,1,0,2,2,1,1,1},
                {1,1,1,1,0,1,1,2,2},
                {1,1,1,1,0,2,1,2,1},
                {1,1,1,1,1,0,1,2,2},
                {1,1,1,1,1,1,0,2,2},
                {1,1,1,1,1,1,1,1,2},
                {1,1,1,1,1,1,1,2,1},
                {1,1,1,1,1,1,1,2,2},
                {1,1,1,1,1,2,0,2,1},
                {1,1,1,1,1,2,1,1,1},
                {1,1,1,1,1,2,1,2,0},
                {1,1,1,1,1,2,1,2,1},
                {1,1,1,1,2,0,1,1,2},
                {1,1,1,1,2,1,0,1,2},
                {1,1,1,1,2,1,1,0,2},
                {1,1,1,1,2,1,1,1,1},
                {1,1,1,1,2,1,1,1,2},
                {1,1,1,1,2,2,0,1,1},
                {1,1,1,1,2,2,1,0,1},
                {1,1,1,1,2,2,1,1,0},
                {1,1,1,1,2,2,1,1,1},
                {1,1,2,0,1,1,1,2,1},
                {1,1,2,0,1,2,1,2,0},
                {1,1,2,0,2,0,1,1,2},

                // run6 solutions (107 solutions - final)
                {1,1,2,0,2,1,1,1,1},
                {1,1,2,0,2,2,1,1,0},
                {1,1,2,1,0,1,1,2,1},
                {1,1,2,1,0,2,1,2,0},
                {1,1,2,1,1,0,0,2,2},
                {1,1,2,1,1,0,1,1,2},
                {1,1,2,1,1,0,1,2,1},
                {1,1,2,1,1,0,1,2,2},
                {1,1,2,1,1,1,0,2,1},
                {1,1,2,1,1,1,1,1,1},
                {1,1,2,1,1,1,1,2,0},
                {1,1,2,1,1,1,1,2,1},
                {1,1,2,1,1,2,0,2,0},
                {1,1,2,1,1,2,1,1,0},
                {1,1,2,1,1,2,1,2,0},
                {1,1,2,1,2,0,0,1,2},
                {1,1,2,1,2,0,1,0,2},
                {1,1,2,1,2,0,1,1,1},
                {1,1,2,1,2,0,1,1,2},
                {1,1,2,1,2,1,0,1,1},
                {1,1,2,1,2,1,1,0,1},
                {1,1,2,1,2,1,1,1,0},
                {1,1,2,1,2,1,1,1,1},
                {1,1,2,1,2,2,1,1,0}
        };

        for (int[] values : solutionValues) {
            Map<String, Integer> solution = new LinkedHashMap<>();
            for (int i = 0; i < VARIABLES.size(); i++) {
                solution.put(VARIABLES.get(i), values[i]);
            }
            allSolutions.add(solution);

            // Create string representation for fast lookup
            String solStr = solutionToString(solution);
            allSolutionStrings.add(solStr);
        }

        System.out.println("Loaded " + allSolutions.size() + " total solutions.");
    }

    private String solutionToString(Map<String, Integer> solution) {
        return solution.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    /**
     * Mark a solution as found during search.
     * Returns true if it was a valid solution and wasn't already marked as found.
     */
    public boolean markSolutionAsFound(Map<String, Integer> solution) {
        String solStr = solutionToString(solution);
        if (allSolutionStrings.contains(solStr) && !foundSolutionsSet.contains(solStr)) {
            foundSolutionsSet.add(solStr);
            foundSolutionsList.add(solution);
            return true;
        }
        return false;
    }

    /**
     * Mark a solution as found using varargs.
     * Example: markSolutionAsFound("x11", 1, "x12", 1, ...)
     */
    public boolean markSolutionAsFound(String... varValuePairs) {
        Map<String, Integer> solution = partial(varValuePairs);
        return markSolutionAsFound(solution);
    }

    /**
     * Check if a specific solution has already been found.
     */
    public boolean isSolutionFound(Map<String, Integer> solution) {
        return foundSolutionsSet.contains(solutionToString(solution));
    }

    /**
     * Get all matching UNFOUND solutions for a partial assignment.
     * This is the key method for your search process!
     */
    public List<Map<String, Integer>> getUnfoundMatchingSolutions(Map<String, Integer> partialSolution) {
        List<Map<String, Integer>> unfound = new ArrayList<>();
        for (Map<String, Integer> fullSolution : allSolutions) {
            // Check if full solution matches the partial
            boolean matches = true;
            for (Map.Entry<String, Integer> entry : partialSolution.entrySet()) {
                String var = entry.getKey();
                Integer partialValue = entry.getValue();
                Integer fullValue = fullSolution.get(var);

                if (fullValue == null || !fullValue.equals(partialValue)) {
                    matches = false;
                    break;
                }
            }

            // If it matches and hasn't been found yet, add it
            if (matches && !foundSolutionsSet.contains(solutionToString(fullSolution))) {
                unfound.add(fullSolution);
            }
        }
        return unfound;
    }

    /**
     * Check if there exists at least one UNFOUND solution matching the partial.
     */
    public boolean hasUnfoundMatchingSolution(Map<String, Integer> partialSolution) {
        for (Map<String, Integer> fullSolution : allSolutions) {
            boolean matches = true;
            for (Map.Entry<String, Integer> entry : partialSolution.entrySet()) {
                String var = entry.getKey();
                Integer partialValue = entry.getValue();
                Integer fullValue = fullSolution.get(var);

                if (fullValue == null || !fullValue.equals(partialValue)) {
                    matches = false;
                    break;
                }
            }

            if (matches && !foundSolutionsSet.contains(solutionToString(fullSolution))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all solutions that have NOT been found yet.
     */
    public List<Map<String, Integer>> getAllUnfoundSolutions() {
        List<Map<String, Integer>> unfound = new ArrayList<>();
        for (Map<String, Integer> solution : allSolutions) {
            if (!foundSolutionsSet.contains(solutionToString(solution))) {
                unfound.add(solution);
            }
        }
        return unfound;
    }

    /**
     * Get all solutions that HAVE been found.
     */
    public List<Map<String, Integer>> getFoundSolutions() {
        return new ArrayList<>(foundSolutionsList);
    }

    /**
     * Get statistics about found vs total solutions.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSolutions", allSolutions.size());
        stats.put("foundSolutions", foundSolutionsSet.size());
        stats.put("remainingSolutions", allSolutions.size() - foundSolutionsSet.size());
        stats.put("completionPercentage", (foundSolutionsSet.size() * 100.0) / allSolutions.size());
        return stats;
    }

    /**
     * Reset the found solutions tracker (useful for new search runs).
     */
    public void resetFoundSolutions() {
        foundSolutionsSet.clear();
        foundSolutionsList.clear();
    }

    /**
     * Convenience method to create a partial solution map.
     */
    public static Map<String, Integer> partial(String... varValuePairs) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < varValuePairs.length; i += 2) {
            map.put(varValuePairs[i], Integer.parseInt(varValuePairs[i+1]));
        }
        return map;
    }
}