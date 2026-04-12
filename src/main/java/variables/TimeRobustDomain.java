package variables;

import java.util.*;
import constraints.Constraint;

/**
 * Optimized Time Robust Propagator for ACE Solver.
 * Manages three states for every value:
 * 1. Fully Robust: In var.dom & aliveWords & baseWords (Case 1)
 * 2. Support Only: In aliveWords, but NOT in var.dom or baseWords (Case 2)
 * 3. Infeasible: Deleted from aliveWords - dead for everyone (Case 3)
 */
public class TimeRobustDomain implements RobustDomain{
    private final Variable var;
    private final int n;            // Horizon
    private final int K;            // Forward sequence depth (successors)
    private final int H;            // Backward sequence depth (predecessors)
    private final int offset;       // Predicate: next >= current + offset

    private long[] aliveValues;      // Shadow Domain (Cases 1 & 2)
    private long[] baseValues;       // Robust Bases (Case 1 Only)

    private class DomainState {
        final long[] savedAlive;
        final long[] savedBase;

        DomainState(long[] alive, long[] base) {
            this.savedAlive = alive.clone();
            this.savedBase = base.clone();
        }
    }

    // Update your history map definition
    private Map<Integer, DomainState> history = new HashMap<>();
    private int lastModifiedLevel = -1;

    public TimeRobustDomain(Variable var, int n, int K, int H, int offset) {
        this.var = var;
        this.n = n;
        this.K = K;
        this.H = H;
        this.offset = offset;

        int numLongs = (n + 63) / 64; //Adding 63 to round up to the next integer
        this.aliveValues = new long[numLongs];
        this.baseValues = new long[numLongs];

        Arrays.fill(aliveValues, -1L);
        // Clean up bits beyond the horizon 'n'
        if (n % 64 != 0) {
            aliveValues[numLongs - 1] &= (1L << (n % 64)) - 1;
        }
        updateRobustness();
    }

    /**
     * CASE 2: Consistent but not Robust (or Search-based Refutation).
     * Removes 'v' from the solver's domain (cannot be a solution),
     * but KEEPS it in the shadow (aliveWords) so it can still support neighbors.
     */
    @Override
    public void removeAsBaseOnly(int v, int currentLevel) {
        // 1. Ensure we have a backup of the state prior to this level's changes
        saveBeforeModification(currentLevel);

        // 2. Remove from the solver's physical domain
        var.dom.removeElementary(v);

        // 3. Clear from baseValues (The virtual domain's "Robust" subset)
        baseValues[v >> 6] &= ~(1L << (v & 63));
    }

    @Override
    public boolean checkVariableForRC(int currentLevel) {

        // --- 1. PRE-PROCESSING: Ensure Robustness is Fresh ---
        // We run the Shadow Sync first for EVERYONE.
        // This ensures that if a neighbor lost physical support, we know it.
        boolean shadowChanged = false;
        for (int v = nextSetBit(aliveValues, 0); v >= 0; v = nextSetBit(aliveValues, v + 1)) {
            if (var.dom.contains(v)) continue;

            boolean gacSupported = true;
            for (Constraint c : var.ctrs) {
                if (!c.seekFirstSupportWith(c.positionOf(var), v)) {
                    gacSupported = false;
                    break;
                }
            }
            if (!gacSupported) {
                clearShadowBit(v, currentLevel);
                shadowChanged = true;
            }
        }

        // CRITICAL: If the physical shadow changed, we MUST recompute
        // which values are robust (the baseValues bitset).
        if (shadowChanged) {
            updateRobustness();
        }

        // --- 1.5. THE WIPEOUT CHECK ---
        if (var.dom.size() == 0) {
            return false; // Fail immediately if the physical domain is empty!
        }

        // --- 2. THE ASSIGNED VARIABLE CHECK (Now safe) ---
        if (var.dom.size() == 1) {
            int v = var.dom.single();

            // We don't need to re-check GAC here because Part 1
            // would have already cleared the shadow bit if 'v' wasn't GAC.
            // We only check if 'v' is still in the refreshed baseValues.
            if (!isBitSet(baseValues, v)) {
                return false; // Fail: Assignment lost its required backups
            }
            return true;
        }

        // --- 3. THE UNASSIGNED PRUNING ---
        int v = var.dom.first();
        while (v != -1) {
            int nextV = var.dom.next(v);
            if (!isBitSet(baseValues, v)) {
                var.dom.removeElementary(v);
                if (var.dom.size() < 1) return false;
            }
            v = nextV;
        }

        return true;
    }

    /**
     * Restoration logic for backtracking.
     */
    @Override
    public void backtrackTo(int targetLevel) {
        int oldestAbandoned = -1;
        int maxRemaining = -1; // Use your default initial level here (e.g., 0 or -1)

        // A single pass to find BOTH metrics without stream overhead!
        for (int lvl : history.keySet()) {
            if (lvl >= targetLevel) {
                if (oldestAbandoned == -1 || lvl < oldestAbandoned) {
                    oldestAbandoned = lvl;
                }
            } else {
                if (lvl > maxRemaining) {
                    maxRemaining = lvl;
                }
            }
        }

        // 1. Restore BOTH bitsets from that snapshot (if it exists)
        if (oldestAbandoned != -1) {
            DomainState state = history.get(oldestAbandoned);
            this.aliveValues = state.savedAlive.clone();
            this.baseValues = state.savedBase.clone();
            // Restored baseValues are already correct!
        }

        // 2. Cleanup the abandoned history
        history.keySet().removeIf(lvl -> lvl >= targetLevel);

        // 3. Reset the modification pointer to the actual last recorded level
        this.lastModifiedLevel = maxRemaining;
    }

    /**
     * A variable is robust if there is at least one value that satisfies
     * the physical constraints (Shadow) AND the robustness requirements (Bases).
     */
    @Override
    public boolean isRobust() {
        for (long word : baseValues) {
            if (word != 0) return true;
        }
        return false;
    }

    /**
     * Returns the smallest value currently in the Shadow Domain (aliveValues).
     * Returns -1 if the shadow domain is empty.
     */
    @Override
    public int firstValue() {
        for (int i = 0; i < aliveValues.length; i++) {
            if (aliveValues[i] != 0) {
                return (i << 6) + Long.numberOfTrailingZeros(aliveValues[i]);
            }
        }
        return -1;
    }

    /**
     * Returns the largest value currently in the Shadow Domain (aliveValues).
     * Returns -1 if the shadow domain is empty.
     */
    @Override
    public int lastValue() {
        for (int i = aliveValues.length - 1; i >= 0; i--) {
            if (aliveValues[i] != 0) {
                // 63 - LeadingZeros gives the index of the highest set bit in the long
                return (i << 6) + (63 - Long.numberOfLeadingZeros(aliveValues[i]));
            }
        }
        return -1;
    }

    /**
     * Calculates Robust Bases (Case 1) using bit-shifts on the Shadow Domain.
     * Logic: Base = Alive AND (has K successors) AND (has H predecessors).
     */
    private void updateRobustness() {
        // Forward reachability (K steps)
        long[] hasAsc = aliveValues.clone();
        for (int i = 0; i < K; i++) {
            hasAsc = shiftAndIntersect(hasAsc, offset, true);
        }

        // Backward reachability (H steps)
        long[] hasDesc = aliveValues.clone();
        for (int i = 0; i < H; i++) {
            hasDesc = shiftAndIntersect(hasDesc, offset, false);
        }

        // Combine: Update the current baseValues based on current physical aliveValues
        for (int i = 0; i < baseValues.length; i++) {
            baseValues[i] = aliveValues[i] & hasAsc[i] & hasDesc[i];
        }
    }

    /**
     * CASE 3: Physical Infeasibility.
     * Called when a hard constraint is violated or a branch is dead.
     * Kills the value as a potential base AND as a backup for others.
     */
    private void clearShadowBit(int val, int level) {
        // 1. Ensure we have a backup
        saveBeforeModification(level);

        // 2. Modify: Clear the bit in the shadow
        aliveValues[val >> 6] &= ~(1L << (val & 63));
    }

    /**
     * Core bit-shifter for O(N/64) neighbor checking.
     */
    private long[] shiftAndIntersect(long[] source, int delta, boolean forward) {
        int wordShift = delta / 64;
        int bitShift = delta % 64;
        long[] result = new long[source.length];

        if (forward) { // Looking for SUCCESSORS (t + delta) -> Shift bits RIGHT
            for (int i = 0; i < source.length - wordShift; i++) {
                long val = source[i + wordShift] >>> bitShift;
                if (bitShift > 0 && i + wordShift + 1 < source.length) {
                    // Pull bits from the next word
                    val |= (source[i + wordShift + 1] << (64 - bitShift));
                }
                result[i] = val & aliveValues[i];
            }
        } else { // Looking for PREDECESSORS (t - delta) -> Shift bits LEFT
            for (int i = source.length - 1; i >= wordShift; i--) {
                long val = source[i - wordShift] << bitShift;
                if (bitShift > 0 && i - wordShift - 1 >= 0) {
                    // Pull bits from the previous word
                    val |= (source[i - wordShift - 1] >>> (64 - bitShift));
                }
                result[i] = val & aliveValues[i];
            }
        }
        return result;
    }

    private void saveBeforeModification(int currentLevel) {
        // Only save if this is the FIRST time we touch this variable
        // at this specific search depth.
        if (currentLevel > lastModifiedLevel) {
            // Capture the state BEFORE any changes (Shadow or Base)
            history.put(currentLevel, new DomainState(this.aliveValues, this.baseValues));
            lastModifiedLevel = currentLevel;

            // Register the variable once for this level
            var.problem.solver.solverTracker.markAsModified(this.var, currentLevel);
        }
    }

    // --- Bit Utilities ---
    private boolean isBitSet(long[] words, int i) {
        if (i < 0 || i >= n) return false;
        return (words[i >> 6] & (1L << (i & 63))) != 0;
    }

    private int nextSetBit(long[] words, int from) {
        int u = from >> 6;
        if (u >= words.length) return -1;
        long word = words[u] & (-1L << (from & 63));
        while (true) {
            if (word != 0) return (u << 6) + Long.numberOfTrailingZeros(word);
            if (++u == words.length) return -1;
            word = words[u];
        }
    }

}