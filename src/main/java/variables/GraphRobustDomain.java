package variables;

import java.util.*;
import constraints.Constraint;
import constraints.global.Count;
import utility.RobustUtils;
import java.util.function.BiPredicate;

public class GraphRobustDomain implements RobustDomain {
    private final Variable var;
    private final int n, H, K;

    // Static Graph
    private final BitSet[] succAsc, succDesc;

    // Search State (Shadow and Bases)
    private BitSet alive;  // Case 2: Shadow (Consistent but not necessarily in var.dom)
    private BitSet isBase; // Case 1: Robust & In var.dom

    private final BitSet[] savedAlive;
    private final BitSet[] savedBase;
    private final int[] savedLevels;
    private int historySize = 0;
    private int lastModifiedLevel = -1;

    public GraphRobustDomain(Variable var, int n, int H, int K) {
        this(var, n, H, K, null);
    }

    public GraphRobustDomain(Variable var, int n, int H, int K, BiPredicate<Integer, Integer> customPredicate) {
        this.var = var;
        this.n = n;
        this.H = H;
        this.K = K;

        this.alive = new BitSet(n);
        this.isBase = new BitSet(n);
        this.alive.set(0, n);

        int maxDepth = var.problem.variables.length + 2;
        this.savedAlive = new BitSet[maxDepth];
        this.savedBase = new BitSet[maxDepth];
        this.savedLevels = new int[maxDepth];
        for(int i = 0; i < maxDepth; i++) {
            this.savedAlive[i] = new BitSet(n);
            this.savedBase[i] = new BitSet(n);
        }

        this.succAsc = new BitSet[n];
        this.succDesc = new BitSet[n];
        for(int i=0; i<n; i++) {
            succAsc[i] = new BitSet(n);
            succDesc[i] = new BitSet(n);
        }

        if (customPredicate != null) {
            buildInitialGraph(customPredicate);
        } else {
            buildInitialGraph();
        }
        updateRobustness(); // Initial calculation
    }

    @Override
    public void removeAsBaseOnly(int v, int currentLevel) {
        saveBeforeModification(currentLevel);
        var.dom.removeElementary(v);
        isBase.clear(v);
    }

    @Override
    public boolean checkVariableForRC(int currentLevel) {
        // --- 1. SHADOW SYNC (Crucial for Job Shop / Nurse Rostering) ---
        boolean shadowChanged = false;
        for (int v = alive.nextSetBit(0); v >= 0; v = alive.nextSetBit(v + 1)) {
            // If it's in the physical domain, we assume it's currently supported
            if (var.dom.contains(v)) continue;

            // Check if this shadow value is still supported by other constraints
            boolean supported = true;
            for (Constraint c : var.ctrs) {
                if(c instanceof Count || c instanceof constraints.ConstraintExtension || Arrays.stream(c.scp).anyMatch(var->var.id().contains("aux")))
                    continue;
                if (!c.seekFirstSupportWith(c.positionOf(var), v)) {
                    supported = false;
                    break;
                }
            }
            if (!supported) {
                saveBeforeModification(currentLevel);
                alive.clear(v);
                shadowChanged = true;
            }
        }

        // If the shadow changed, re-evaluate which bases are still robust
        if (shadowChanged) {
            updateRobustness();
        }

        // --- 2. WIPEOUT & ASSIGNMENT CHECKS ---
        if (var.dom.size() == 0) return false;

        if (var.dom.size() == 1) {
            int v = var.dom.single();
            return isBase.get(v); // Fail if the assignment isn't robust
        }

        // --- 3. FULL DOMAIN PRUNING (Disabled for CSOP) ---
        // In a CSOP, robustness is an objective function, not a hard constraint.
        // We do NOT hard prune values that lack K backups, because a variable is 
        // allowed to have 0 backups (it just contributes 0 to the objective sum).
        
        return true;
    }

    private void updateRobustness() {
        for (int i = 0; i < n; i++) {
            if (alive.get(i) && var.dom.contains(i) &&
                    hasPath(i, K, succAsc) && hasPath(i, H, succDesc)) {
                isBase.set(i);
            } else {
                isBase.clear(i);
            }
        }
    }

    private boolean hasPath(int root, int dist, BitSet[] adj) {
        if (dist == 0) return true;
        BitSet currentLayer = new BitSet(n);
        currentLayer.set(root);
        for (int d = 1; d <= dist; d++) {
            BitSet nextLayer = new BitSet(n);
            for (int v = currentLayer.nextSetBit(0); v >= 0; v = currentLayer.nextSetBit(v+1)) {
                nextLayer.or(adj[v]);
            }
            nextLayer.and(alive); // Must stay within the shadow
            if (nextLayer.isEmpty()) return false;
            currentLayer = nextLayer;
        }
        return true;
    }

    @Override
    public void backtrackTo(int targetLevel) {
        boolean restored = false;
        while (historySize > 0 && savedLevels[historySize - 1] >= targetLevel) {
            historySize--;
            restored = true;
        }

        if (restored) {
            this.alive.clear();
            this.alive.or(savedAlive[historySize]);
            
            this.isBase.clear();
            this.isBase.or(savedBase[historySize]);
        }

        this.lastModifiedLevel = historySize > 0 ? savedLevels[historySize - 1] : -1;
    }

    private void saveBeforeModification(int currentLevel) {
        if (currentLevel > lastModifiedLevel) {
            savedAlive[historySize].clear();
            savedAlive[historySize].or(this.alive);
            
            savedBase[historySize].clear();
            savedBase[historySize].or(this.isBase);
            
            savedLevels[historySize] = currentLevel;
            historySize++;
            
            lastModifiedLevel = currentLevel;
            var.problem.solver.solverTracker.markAsModified(this.var, currentLevel);
        }
    }

    // [buildInitialGraph and explore methods remain the same as previous logic]

    private void buildInitialGraph() {
        BiPredicate<Integer, Integer> alwaysTrue = (a, b) -> true;
        BiPredicate<Integer, Integer> rp = RobustUtils.rightPredicate != null ? RobustUtils.rightPredicate : alwaysTrue;
        BiPredicate<Integer, Integer> lp = RobustUtils.leftPredicate != null ? RobustUtils.leftPredicate : alwaysTrue;
        for (int i = 0; i < n; i++) {
            explore(i, i, 0, K, rp, true);
            explore(i, i, 0, H, lp, false);
        }
    }

    private void buildInitialGraph(BiPredicate<Integer, Integer> pr) {
        for (int i = 0; i < n; i++) {
            explore(i, i, 0, K, pr, true);
            explore(i, i, 0, H, pr, false);
        }
    }

    private boolean explore(int start, int curr, int d, int L, BiPredicate<Integer, Integer> pr, boolean asc) {
        if (d == L) return true;
        boolean can = false;
        for (int j = 0; j < n; j++) {
            if (pr.test(start, j)) { // Using base-index comparison (Anchor policy)
                if (explore(start, j, d + 1, L, pr, asc)) {
                    (asc ? succAsc : succDesc)[curr].set(j);
                    can = true;
                }
            }
        }
        return can;
    }

    @Override public boolean isRobust() { return !isBase.isEmpty(); }
    @Override public int firstValue() { return alive.nextSetBit(0); }
    @Override public int lastValue() { return alive.previousSetBit(n - 1); }

    @Override
    public int getRobustDomainSize() {
        return isBase.cardinality();
    }

    @Override
    public int getRobustWeight(int v) {
        if (v < 0 || v >= n) return 0;
        return succAsc[v].cardinality() + succDesc[v].cardinality();
    }

    @Override
    public boolean isRobustBase(int v) {
        if (v < 0 || v >= n) return false;
        return isBase.get(v);
    }
}