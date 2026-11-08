package dev.gungang.acoustics.server;

import java.util.*;

/**
 * The free-equation damage curve of {@link DamageFalloff}: + - * / ^ (power, right-associative), parentheses, unary
 * minus, constants pi / e, the variables d t base vs ve k start end, and the functions exp ln log log10 sqrt abs floor
 * ceil round min max pow clamp sin cos.
 */
public final class FalloffExpression {
    /** Variables in evaluation order. */
    public static final List<String> VARIABLES = List.of("d", "t", "base", "vs", "ve", "k", "start", "end");
    public interface Node { double eval(double[] vars); }
    private final String text;
    private int pos;
    private FalloffExpression(String text) { this.text = text; }

    public static Node compile(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("방정식을 입력하세요.");
        if (text.length() > 200) throw new IllegalArgumentException("방정식은 200자 이하로 입력하세요.");
        var parser = new FalloffExpression(text);
        var node = parser.sum(0);
        parser.space();
        if (parser.pos < text.length()) throw parser.error("알 수 없는 문자 '" + text.charAt(parser.pos) + "'");
        return node;
    }
    private IllegalArgumentException error(String what) { return new IllegalArgumentException("방정식 오류 (" + (pos + 1) + "번째 글자): " + what); }
    private void space() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }
    private boolean eat(char c) { space(); if (pos < text.length() && text.charAt(pos) == c) { pos++; return true; } return false; }
    private void depth(int depth) { if (depth > 64) throw error("너무 깊게 중첩되었습니다"); }

    private Node sum(int depth) {
        depth(depth);
        var left = product(depth + 1);
        while (true) {
            if (eat('+')) { var a = left; var b = product(depth + 1); left = v -> a.eval(v) + b.eval(v); }
            else if (eat('-')) { var a = left; var b = product(depth + 1); left = v -> a.eval(v) - b.eval(v); }
            else return left;
        }
    }
    private Node product(int depth) {
        var left = unary(depth + 1);
        while (true) {
            if (eat('*')) { var a = left; var b = unary(depth + 1); left = v -> a.eval(v) * b.eval(v); }
            else if (eat('/')) { var a = left; var b = unary(depth + 1); left = v -> a.eval(v) / b.eval(v); }
            else return left;
        }
    }
    private Node unary(int depth) {
        depth(depth);
        if (eat('-')) { var a = unary(depth + 1); return v -> -a.eval(v); }
        if (eat('+')) return unary(depth + 1);
        return power(depth + 1);
    }
    private Node power(int depth) {
        var base = atom(depth + 1);
        if (eat('^')) { var exponent = unary(depth + 1); return v -> Math.pow(base.eval(v), exponent.eval(v)); }
        return base;
    }
    private Node atom(int depth) {
        depth(depth);
        space();
        if (pos >= text.length()) throw error("식이 끝났습니다");
        char c = text.charAt(pos);
        if (eat('(')) { var inner = sum(depth + 1); if (!eat(')')) throw error("')'가 필요합니다"); return inner; }
        if (Character.isDigit(c) || c == '.') {
            int start = pos;
            while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) pos++;
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E') && pos + 1 < text.length()
                    && (Character.isDigit(text.charAt(pos + 1)) || "+-".indexOf(text.charAt(pos + 1)) >= 0)) {
                pos += 2; while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            double value;
            try { value = Double.parseDouble(text.substring(start, pos)); } catch (NumberFormatException e) { throw error("잘못된 숫자"); }
            return v -> value;
        }
        if (Character.isLetter(c)) {
            int start = pos;
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) pos++;
            String name = text.substring(start, pos).toLowerCase(Locale.ROOT);
            if (eat('(')) {
                var args = new ArrayList<Node>();
                if (!eat(')')) {
                    do args.add(sum(depth + 1)); while (eat(','));
                    if (!eat(')')) throw error("')'가 필요합니다");
                }
                return function(name, args);
            }
            int index = VARIABLES.indexOf(name);
            if (index >= 0) return v -> v[index];
            if (name.equals("pi")) return v -> Math.PI;
            if (name.equals("e")) return v -> Math.E;
            throw error("알 수 없는 이름 '" + name + "'");
        }
        throw error("알 수 없는 문자 '" + c + "'");
    }
    private Node function(String name, List<Node> args) {
        int count = switch (name) {
            case "exp", "ln", "log", "log10", "sqrt", "abs", "floor", "ceil", "round", "sin", "cos" -> 1;
            case "min", "max", "pow" -> 2;
            case "clamp" -> 3;
            default -> throw error("알 수 없는 함수 '" + name + "'");
        };
        if (args.size() != count) throw error(name + "() 인자는 " + count + "개입니다");
        Node a = args.get(0), b = count > 1 ? args.get(1) : null, c = count > 2 ? args.get(2) : null;
        return switch (name) {
            case "exp" -> v -> Math.exp(a.eval(v));
            case "ln", "log" -> v -> Math.log(a.eval(v));
            case "log10" -> v -> Math.log10(a.eval(v));
            case "sqrt" -> v -> Math.sqrt(a.eval(v));
            case "abs" -> v -> Math.abs(a.eval(v));
            case "floor" -> v -> Math.floor(a.eval(v));
            case "ceil" -> v -> Math.ceil(a.eval(v));
            case "round" -> v -> Math.rint(a.eval(v));
            case "sin" -> v -> Math.sin(a.eval(v));
            case "cos" -> v -> Math.cos(a.eval(v));
            case "min" -> v -> Math.min(a.eval(v), b.eval(v));
            case "max" -> v -> Math.max(a.eval(v), b.eval(v));
            case "pow" -> v -> Math.pow(a.eval(v), b.eval(v));
            default -> v -> Math.max(b.eval(v), Math.min(c.eval(v), a.eval(v)));
        };
    }
}
