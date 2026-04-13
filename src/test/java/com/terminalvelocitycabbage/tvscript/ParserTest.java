package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.util.AstPrinter;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private Expression parse(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    private String print(Expression expr) {
        return new AstPrinter().print(expr);
    }

    @Test
    void testLiterals() {
        assertEquals("123", print(parse("123")));
        assertEquals("123.456", print(parse("123.456")));
        assertEquals("true", print(parse("true")));
        assertEquals("false", print(parse("false")));
        assertEquals("none", print(parse("none")));
        assertEquals("hello", print(parse("\"hello\"")));
    }

    @Test
    void testUnary() {
        assertEquals("(- 123)", print(parse("-123")));
        assertEquals("(! true)", print(parse("!true")));
        assertEquals("(- (- 123))", print(parse("- -123")));
    }
    @Test
    void testBinary() {
        assertEquals("(+ 1 2)", print(parse("1 + 2")));
        assertEquals("(- 1 2)", print(parse("1 - 2")));
        assertEquals("(* 1 2)", print(parse("1 * 2")));
        assertEquals("(/ 1 2)", print(parse("1 / 2")));
        assertEquals("(% 1 2)", print(parse("1 % 2")));
        assertEquals("(+ 1 (* 2 3))", print(parse("1 + 2 * 3")));
        assertEquals("(* (group (+ 1 2)) 3)", print(parse("(1 + 2) * 3")));
    }

    @Test
    void testLogical() {
        assertEquals("(and true false)", print(parse("true and false")));
        assertEquals("(or true false)", print(parse("true or false")));
        assertEquals("(or (and a b) (and c d))", print(parse("a and b or c and d")));
    }

    @Test
    void testComparisonAndEquality() {
        assertEquals("(> 1 2)", print(parse("1 > 2")));
        assertEquals("(>= 1 2)", print(parse("1 >= 2")));
        assertEquals("(< 1 2)", print(parse("1 < 2")));
        assertEquals("(<= 1 2)", print(parse("1 <= 2")));
        assertEquals("(== 1 2)", print(parse("1 == 2")));
        assertEquals("(!= 1 2)", print(parse("1 != 2")));
    }
    @Test
    void testTernary() {
        assertEquals("(? true 1 2)", print(parse("true ? 1 : 2")));
        assertEquals("(? true (? false 1 2) 3)", print(parse("true ? false ? 1 : 2 : 3")));
        assertEquals("(? true 1 (? false 2 3))", print(parse("true ? 1 : false ? 2 : 3")));
    }

    @Test
    void testInvalidCode() {
        assertNull(parse("1 + * 2"));
        assertNull(parse("(1 + 2"));
        assertNull(parse("1 + )"));
    }
    @Test
    void testStringInterpolation() {
        // "hello {name}!" -> (interpolation hello name !)
        assertEquals("(interpolation hello  name !)", print(parse("\"hello {name}!\"")));
    }
    @Test
    void testComplexExpression() {
        assertEquals("(or (and (== (+ 1 2) 3) (!= 4 5)) (group (> 6 7)))", 
            print(parse("1 + 2 == 3 and 4 != 5 or (6 > 7)")));
    }
}
