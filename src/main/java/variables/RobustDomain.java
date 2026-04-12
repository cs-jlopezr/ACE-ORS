package variables;

public interface RobustDomain {

    public void removeAsBaseOnly(int v, int currentLevel);

    public boolean checkVariableForRC(int currentLevel);

    public void backtrackTo(int targetLevel);

    public boolean isRobust();

    public int firstValue();

    public int lastValue();

}
