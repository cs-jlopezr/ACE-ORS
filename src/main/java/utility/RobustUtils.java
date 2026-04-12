package utility;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.*;

public class RobustUtils {

    enum POSSIBLE_PREDICATE {
        G(">"),
        L("<"),
        GE(">="),
        LE("<="),
        EQ("="),
        NEQ("!=");

        private final String symbol;

        POSSIBLE_PREDICATE(String symbol) {
            this.symbol = symbol;
        }

        public static POSSIBLE_PREDICATE fromSymbol(String symbol) {
            for (POSSIBLE_PREDICATE pred : values()) {
                if (pred.symbol.equals(symbol)) {
                    return pred;
                }
            }
            throw new IllegalArgumentException("No enum constant with symbol " + symbol);
        }
    }

    public enum POSSIBLE_POLICY {

        ANCHOR("anchor"),
        STRIDE("stride");

        private final String symbol;

        POSSIBLE_POLICY(String symbol) {
            this.symbol = symbol;
        }

        public static POSSIBLE_POLICY fromSymbol(String symbol) {
            for (POSSIBLE_POLICY pred : values()) {
                if (pred.symbol.equals(symbol)) {
                    return pred;
                }
            }
            throw new IllegalArgumentException("No enum constant with symbol " + symbol);
        }
    }


    public static BiPredicate<Integer, Integer> leftPredicate;
    public static BiPredicate<Integer, Integer> rightPredicate;
    public static POSSIBLE_POLICY policy;
    public static int directions = 0; // 0 for both, -1 for left and 1 for right
    public static int h = 0;
    public static int k = 0;

    public static Function<Integer, Integer> parse(String expr) {
        // Convert to Reverse Polish Notation (Shunting-yard algorithm)
        List<String> rpn = toRPN(expr);

        // Build a stack of lambdas instead of raw numbers
        Deque<Function<Integer, Integer>> stack = new ArrayDeque<>();
        for (String token : rpn) {
            switch (token) {
                case "+": {
                    Function<Integer, Integer> b = stack.pop();
                    Function<Integer, Integer> a = stack.pop();
                    stack.push(x -> a.apply(x) + b.apply(x));
                }
                break;
                case "-": {
                    Function<Integer, Integer> b = stack.pop();
                    Function<Integer, Integer> a = stack.pop();
                    stack.push(x -> a.apply(x) - b.apply(x));
                }
                break;
                case "*": {
                    Function<Integer, Integer> b = stack.pop();
                    Function<Integer, Integer> a = stack.pop();
                    stack.push(x -> a.apply(x) * b.apply(x));
                }
                break;
                case "/": {
                    Function<Integer, Integer> b = stack.pop();
                    Function<Integer, Integer> a = stack.pop();
                    stack.push(x -> a.apply(x) / b.apply(x)); // integer division
                }
                break;
                case "%": {
                    Function<Integer, Integer> b = stack.pop();
                    Function<Integer, Integer> a = stack.pop();
                    stack.push(x -> a.apply(x) % b.apply(x));
                }
                break;
                case "x":
                    stack.push(x -> x);
                    break;
                default:
                    int value = Integer.parseInt(token);
                    stack.push(x -> value);
                    break;
            }
        }
        return stack.pop(); // final lambda
    }

    private static List<String> toRPN(String expr) {
        // very simplified tokenizer + shunting-yard
        StringTokenizer tokenizer = new StringTokenizer(expr, "+-*/()% ", true);
        List<String> output = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        Map<String, Integer> prec = Map.of(
                "+", 1, "-", 1,
                "*", 2, "/", 2, "%", 2
        );

        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken().trim();
            if (token.isEmpty()) continue;

            if (token.matches("\\d+") || token.equals("x")) {
                output.add(token);
            } else if (prec.containsKey(token)) {
                while (!ops.isEmpty() && prec.containsKey(ops.peek()) &&
                        prec.get(ops.peek()) >= prec.get(token)) {
                    output.add(ops.pop());
                }
                ops.push(token);
            } else if (token.equals("(")) {
                ops.push(token);
            } else if (token.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) {
                    output.add(ops.pop());
                }
                ops.pop(); // discard "("
            }
        }
        while (!ops.isEmpty()) output.add(ops.pop());
        return output;
    }

    public static BiPredicate<Integer, Integer> makePredicate(String op, Function<Integer, Integer> base, Function<Integer, Integer> subsequent) {
        POSSIBLE_PREDICATE pred = POSSIBLE_PREDICATE.fromSymbol(op);
        switch (pred) {
            case G:
                return (a, b) -> base.apply(a) > subsequent.apply(b);      // greater than
            case L:
                return (a, b) -> base.apply(a) < subsequent.apply(b);      // less than
            case GE:
                return (a, b) -> base.apply(a) >= subsequent.apply(b);      // less than
            case LE:
                return (a, b) -> base.apply(a) <= subsequent.apply(b);      // less than
            case EQ:
                return (a, b) -> base.apply(a).equals(subsequent.apply(b)); // equality
            case NEQ:
                return (a, b) -> !base.apply(a).equals(subsequent.apply(b)); // inequality
            default:
                throw new IllegalArgumentException("Unknown predicate: " + op);
        }
    }

    public static void initializeSchema(String robustScheme) {
        String[] schema = robustScheme.split(";");
        if (!schema[0].equals("NA")) {
            String[] left = schema[0].trim().substring(1, schema[0].length() - 1).split(",");
            Function<Integer, Integer> baseLeft = parse(left[0]);
            Function<Integer, Integer> subsequentLeft = parse(left[1]);
            leftPredicate = makePredicate(left[2], baseLeft, subsequentLeft);
            h = Integer.parseInt(left[3]);
            directions = -1;
        }

        if (!schema[1].equals("NA")) {
            String[] right = schema[1].trim().substring(1, schema[1].length() - 1).split(",");
            Function<Integer, Integer> baseLeft = parse(right[0]);
            Function<Integer, Integer> subsequentLeft = parse(right[1]);
            rightPredicate = makePredicate(right[2], baseLeft, subsequentLeft);
            k = Integer.parseInt(right[3]);
            directions = directions == -1 ? 0 : 1;
        }
    }

    public static void setPolicy(String rpolicy) {
        policy = POSSIBLE_POLICY.fromSymbol(rpolicy);
    }

}
