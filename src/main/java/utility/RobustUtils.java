package utility;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RobustUtils {

    // --- Symbolic Parameters ---
    public static int h = 0;
    public static int k = 0;
    public static int h_offset = 0;
    public static int k_offset = 0;

    // For TimeRobustDomain specifically
    public static int offset = 0;

    public static BiPredicate<Integer, Integer> leftPredicate;
    public static BiPredicate<Integer, Integer> rightPredicate;
    public static int directions = 0;

    private static final Map<String, Integer> PRECEDENCE = new HashMap<>();
    static {
        PRECEDENCE.put("||", 1); PRECEDENCE.put("&&", 2);
        PRECEDENCE.put("==", 3); PRECEDENCE.put("!=", 3);
        PRECEDENCE.put("<", 4);  PRECEDENCE.put(">", 4);
        PRECEDENCE.put("<=", 4); PRECEDENCE.put(">=", 4);
        PRECEDENCE.put("+", 5);  PRECEDENCE.put("-", 5);
        PRECEDENCE.put("*", 6);  PRECEDENCE.put("/", 6);
        PRECEDENCE.put("!", 7);
    }

    /**
     * @param scheme The symbolic string, e.g., "(x-h_offset==y, h);(x+k_offset==y, k)"
     * @param tdom   True for TimeRobustDomain, False for GraphRobustDomain
     * @param h_val, k_val, h_off, k_off The actual values to use
     */
    public static void initializeSchema(String scheme, boolean tdom, int h_val, int k_val, int h_off, int k_off) {
        // 1. Set the global parameters
        h = h_val;
        k = k_val;
        h_offset = h_off;
        k_offset = k_off;
        directions = 0;

        if (tdom) {
            // In Time-Domain, we enforce the right-only structure
            // and ensure offset is set for the TimeRobustDomain class
            if (scheme.contains(";")) throw new IllegalArgumentException("tdom=true does not support ';' (multi-direction)");

            // We use k_offset for the 'offset' variable used by TimeRobustDomain
            offset = k_offset;
            rightPredicate = parsePredicate(cleanExpression(scheme));
            directions = 1;
        } else {
            // Graph-Domain: Split by semicolon as usual
            String[] sections = scheme.split(";");

            if (!sections[0].equalsIgnoreCase("NA")) {
                leftPredicate = parsePredicate(cleanExpression(sections[0]));
                directions = -1;
            }
            if (sections.length > 1 && !sections[1].equalsIgnoreCase("NA")) {
                rightPredicate = parsePredicate(cleanExpression(sections[1]));
                directions = (directions == -1) ? 0 : 1;
            }
        }
    }

    /**
     * Removes the outer parentheses and the distance parameter,
     * as distance is already handled by h and k variables.
     */
    private static String cleanExpression(String section) {
        String s = section.trim();
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1);
        }
        // If there's a comma separating the logic from the distance name (e.g., "...==y, k")
        // we strip the comma and everything after it because we use the global h/k.
        int lastComma = s.lastIndexOf(",");
        return (lastComma != -1) ? s.substring(0, lastComma).trim() : s;
    }

    private static BiPredicate<Integer, Integer> parsePredicate(String expr) {
        List<String> rpn = toRPN(expr);
        return (x, y) -> evaluate(rpn, x, y) != 0;
    }

    private static Integer evaluate(List<String> rpn, int xVal, int yVal) {
        Deque<Integer> stack = new ArrayDeque<>();
        for (String t : rpn) {
            switch (t) {
                case "x": stack.push(xVal); break;
                case "y": stack.push(yVal); break;
                case "h": stack.push(h); break;
                case "k": stack.push(k); break;
                case "h_offset": stack.push(h_offset); break;
                case "k_offset": stack.push(k_offset); break;
                case "!": stack.push(stack.pop() == 0 ? 1 : 0); break;
                default:
                    if (t.matches("-?\\d+")) {
                        stack.push(Integer.parseInt(t));
                    } else {
                        int b = stack.pop();
                        int a = stack.pop();
                        switch (t) {
                            case "+":  stack.push(a + b); break;
                            case "-":  stack.push(a - b); break;
                            case "*":  stack.push(a * b); break;
                            case "/":  stack.push(a / b); break;
                            case "==": stack.push(a == b ? 1 : 0); break;
                            case "!=": stack.push(a != b ? 1 : 0); break;
                            case ">":  stack.push(a > b ? 1 : 0); break;
                            case "<":  stack.push(a < b ? 1 : 0); break;
                            case ">=": stack.push(a >= b ? 1 : 0); break;
                            case "<=": stack.push(a <= b ? 1 : 0); break;
                            case "&&": stack.push((a != 0 && b != 0) ? 1 : 0); break;
                            case "||": stack.push((a != 0 || b != 0) ? 1 : 0); break;
                        }
                    }
                    break;
            }
        }
        return stack.pop();
    }

    private static List<String> toRPN(String expr) {
        List<String> output = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        // Regex now includes h_offset and k_offset as tokens
        Matcher m = Pattern.compile("h_offset|k_offset|[xyhk]|&&|\\|\\||==|!=|>=|<=|[+\\-*/()<>!]|\\d+").matcher(expr);

        while (m.find()) {
            String t = m.group();
            if (t.matches("h_offset|k_offset|[xyhk]|\\d+")) {
                output.add(t);
            } else if (t.equals("(")) {
                ops.push(t);
            } else if (t.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) output.add(ops.pop());
                if (!ops.isEmpty()) ops.pop();
            } else {
                while (!ops.isEmpty() && PRECEDENCE.getOrDefault(ops.peek(), 0) >= PRECEDENCE.get(t)) {
                    output.add(ops.pop());
                }
                ops.push(t);
            }
        }
        while (!ops.isEmpty()) output.add(ops.pop());
        return output;
    }
}