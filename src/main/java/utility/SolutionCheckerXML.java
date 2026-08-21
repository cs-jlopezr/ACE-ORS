package utility;

import dashboard.Input;
import interfaces.Observers;
import main.Head;
import problem.Problem;
import solver.Solver;
import utility.Reflector;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class SolutionCheckerXML {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java utility.SolutionCheckerXML <instance.xml> <logfile.log>");
            return;
        }
        
        String instancePath = args[0];
        String logPath = args[1];
        
        // 1. Extract solution from the log file
        String logContent = Files.readString(new File(logPath).toPath());
        int startIdx = logContent.lastIndexOf("<instantiation");
        if (startIdx == -1) {
            System.out.println("No solution found in log.");
            return;
        }
        String instantiationStr = logContent.substring(startIdx);
        
        String valuesStr = instantiationStr.substring(instantiationStr.indexOf("<values>") + 8, instantiationStr.indexOf("</values>")).trim();
        String[] valStrs = valuesStr.split("\\s+");
        int[] solution = new int[valStrs.length];
        for (int i = 0; i < valStrs.length; i++) {
            solution[i] = Integer.parseInt(valStrs[i]);
        }
        
        int reportedNeighbors = 0;
        int robStart = instantiationStr.indexOf("<robust_counts>");
        if (robStart != -1) {
            String robStr = instantiationStr.substring(robStart + 15, instantiationStr.indexOf("</robust_counts>")).trim();
            String[] robStrs = robStr.split("\\s+");
            for (String s : robStrs) {
                if (!s.isEmpty()) reportedNeighbors += Integer.parseInt(s);
            }
        }

        // 2. Setup Solver with -tdom=true to initialize TimeRobustDomain for the exact physical check
        String[] setupArgs = new String[]{instancePath, "-activateTRD=true", "-robvars=match-s", "-k=5"};
        Input.loadArguments(setupArgs);
        Head head = new Head();
        Problem problem = head.buildProblem(0);
        head.problem = problem;
        Solver solver = Reflector.buildObject(Solver.class.getName(), Solver.class, head);
        head.solver = solver;
        problem.solver = solver;
        solver.isTimeRobustDomainActive = true; // FORCE exact checking!
        for (Observers.ObserverOnConstruction obs : head.observersConstruction) {
            obs.afterSolverConstruction();
        }

        System.out.println("Checking solution of size: " + solution.length);

        java.lang.reflect.Method tryAssignmentMethod = Solver.class.getDeclaredMethod("tryAssignment", variables.Variable.class, int.class, boolean.class);
        tryAssignmentMethod.setAccessible(true);

        // 3. Perform the N-1 physical neighbor check
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < solution.length; i++) {
            indexes.add(i);
        }
        
        int totalAmountOfNeighbours = 0;
        for (int i = 0; i < solution.length; i++) {
            Collections.rotate(indexes, 1);
            for (int j = 0; j < solution.length - 1; j++) {
                tryAssignmentMethod.invoke(solver, problem.variables[indexes.get(j)], solution[indexes.get(j)], false);
            }
            int lastIdx = indexes.get(solution.length - 1);
            if (problem.variables[lastIdx].robustnessInvolved && problem.variables[lastIdx].robustDomain != null) {
                tryAssignmentMethod.invoke(solver, problem.variables[lastIdx], solution[lastIdx], false);
                totalAmountOfNeighbours += problem.variables[lastIdx].robustDomain.getRobustWeight(solution[lastIdx]);
            }
            solver.backtrackToTheRoot();
        }
        
        System.out.println("The number of neighbours verified is: " + totalAmountOfNeighbours);
        System.out.println("The number of neighbours reported was: " + reportedNeighbors);
        if (totalAmountOfNeighbours == reportedNeighbors) {
            System.out.println("HAPPY END!!!! Exact match guaranteed.");
        } else {
            System.out.println("ERROR: Mismatch in robustness counting!");
        }
    }
}
