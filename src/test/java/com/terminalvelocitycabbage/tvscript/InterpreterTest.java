package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {

    private Object evaluate(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        Expression expression = parser.parse();
        Interpreter interpreter = new Interpreter();
        return interpreter.evaluate(expression);
    }

    @Test
    void testLiterals() {
        assertEquals(123, evaluate("123"));
        assertEquals(123.456, evaluate("123.456"));
        assertEquals(true, evaluate("true"));
        assertEquals(false, evaluate("false"));
        assertEquals(null, evaluate("none"));
        assertEquals("hello", evaluate("\"hello\""));
    }

    @Test
    void testUnary() {
        assertEquals(-123, evaluate("-123"));
        assertEquals(false, evaluate("!true"));
        assertEquals(true, evaluate("!false"));
        assertEquals(-(-123), evaluate("- -123"));
        assertEquals(-123.45, evaluate("-123.45"));
    }

    @Test
    void testBinaryArithmetic() {
        assertEquals(3, evaluate("1 + 2"));
        assertEquals(-1, evaluate("1 - 2"));
        assertEquals(6, evaluate("2 * 3"));
        assertEquals(2, evaluate("6 / 3"));
        assertEquals(1, evaluate("7 % 3"));
        
        assertEquals(3.5, evaluate("1 + 2.5"));
        assertEquals(-1.5, evaluate("1 - 2.5"));
        assertEquals(7.5, evaluate("3 * 2.5"));
        assertEquals(2.0, evaluate("5.0 / 2.5"));
        assertEquals(0.5, evaluate("5.5 % 2.5"));
    }

    @Test
    void testBinaryComparison() {
        assertEquals(true, evaluate("2 > 1"));
        assertEquals(false, evaluate("1 > 2"));
        assertEquals(true, evaluate("2 >= 1"));
        assertEquals(true, evaluate("2 >= 2"));
        assertEquals(true, evaluate("1 < 2"));
        assertEquals(true, evaluate("1 <= 2"));
        assertEquals(true, evaluate("2 <= 2"));
        
        assertEquals(true, evaluate("2.5 > 1"));
        assertEquals(true, evaluate("2.5 > 2.4"));
    }

    @Test
    void testEquality() {
        assertEquals(true, evaluate("1 == 1"));
        assertEquals(false, evaluate("1 == 2"));
        assertEquals(true, evaluate("1 != 2"));
        assertEquals(false, evaluate("1 != 1"));
        
        assertEquals(true, evaluate("1.0 == 1.0"));
        assertEquals(false, evaluate("1 == 1.0")); // Different types
        assertEquals(true, evaluate("1 != 1.0"));
        
        assertEquals(true, evaluate("\"a\" == \"a\""));
        assertEquals(false, evaluate("\"a\" == \"b\""));
        
        assertEquals(true, evaluate("none == none"));
        assertEquals(false, evaluate("none == 1"));
        assertEquals(true, evaluate("none != false"));
    }

    @Test
    void testLogical() {
        assertEquals(true, evaluate("true or false"));
        assertEquals(false, evaluate("true and false"));
        assertEquals(true, evaluate("false or true"));
        assertEquals(false, evaluate("false and true"));
        assertEquals(true, evaluate("true or (1 / 0 == 0)")); // Short-circuit or
        assertEquals(false, evaluate("false and (1 / 0 == 0)")); // Short-circuit and
    }

    @Test
    void testStringInterpolation() {
        assertEquals("val: 123", evaluate("\"val: {123}\""));
        assertEquals("1 + 2 = 3", evaluate("\"{1} + {2} = {1 + 2}\""));
    }

    @Test
    void testRuntimeErrors() {
        assertThrows(RuntimeError.class, () -> evaluate("1 + \"a\""));
        assertThrows(RuntimeError.class, () -> evaluate("- \"a\""));
        assertThrows(RuntimeError.class, () -> evaluate("! 1")); // Only boolean
        assertThrows(RuntimeError.class, () -> evaluate("true and 1")); // Only boolean
        assertThrows(RuntimeError.class, () -> evaluate("1 / 0")); // Integer division by zero
    }
}
