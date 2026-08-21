package variables;

public interface RobustDomain {

    public void removeAsBaseOnly(int v, int currentLevel);

    public boolean checkVariableForRC(int currentLevel);

    public void backtrackTo(int targetLevel);

    public boolean isRobust();

    public int firstValue();

    public int lastValue();

    int getRobustDomainSize();

    default int getRobustWeight(int v) {
        return 0; // Default implementation
    }

    public boolean isRobustBase(int v);
    
    default int getBestPossibleRobustWeight() {
        return 0;
    }
}
