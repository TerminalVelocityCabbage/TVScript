package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FunctionTest {

    private Interpreter interpreter;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    public void setUp() {
        interpreter = new Interpreter();
        System.setOut(new PrintStream(outContent));
    }

    private void run(String source) {
        TVScript.run(source, interpreter);
    }

    private void assertOutput(String expected) {
        assertEquals(expected.trim(), outContent.toString().replace("\r\n", "\n").trim());
    }

    @Test
    public void testBasicFunction() {
        run("""
            function sayHello():
                print "hello"
            
            sayHello()
            """);
        assertOutput("hello");
    }

    @Test
    public void testFunctionWithParameters() {
        run("""
            function greet(string name):
                print "hello {name}"
            
            greet(name: "Junie")
            """);
        assertOutput("hello Junie");
    }

    @Test
    public void testFunctionWithReturn() {
        run("""
            function add(integer a, integer b) -> integer:
                return a + b
            
            print add(a: 5, b: 10)
            """);
        assertOutput("15");
    }

    @Test
    public void testClosure() {
        run("""
            function makeCounter() -> function:
                integer count = 0
                function counter() -> integer:
                    count = count + 1
                    return count
                return counter
            
            var c = makeCounter()
            print c()
            print c()
            """);
        assertOutput("1\n2");
    }

    @Test
    public void testNamedArgumentsOnly() {
        // This test should fail if positional arguments are used
        // But for now let's just test that named works
        run("""
            function sub(integer a, integer b) -> integer:
                return a - b
            
            print sub(b: 5, a: 10)
            """);
        assertOutput("5");
    }

    @Test
    public void testRecursiveFunction() {
        run("""
            function fib(integer n) -> integer:
                if n <= 1: return n
                return fib(n: n - 1) + fib(n: n - 2)
            
            print fib(n: 7)
            """);
        assertOutput("13");
    }

    @Test
    public void testAnonymousFunction() {
        run("""
            var square = (integer n) -> n * n
            print square(n: 4)
            """);
        assertOutput("16");
    }

    @Test
    public void testNativeFunction() {
        run("""
            print abs(n: -10)
            print abs(n: 5.5)
            """);
        assertOutput("10\n5.5");
    }

    @Test
    public void testNativeFunctionWrongArgument() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> {
            run("""
                print abs(wrong: -10)
                """);
        });
        assertEquals("Unexpected argument 'wrong'.", error.getMessage());

        error = assertThrows(RuntimeError.class, () -> {
            run("""
                print clock(extra: 1)
                """);
        });
        assertEquals("Unexpected argument 'extra'.", error.getMessage());
    }

    @Test
    public void testNativeFunctionMissingArgument() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> {
            run("""
                print abs()
                """);
        });
        assertEquals("Missing argument 'n'.", error.getMessage());
    }

    @Test
    public void testCallNonFunction() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> {
            run("""
                var x = 10
                x()
                """);
        });
        assertEquals("Can only call functions and classes.", error.getMessage());
    }

    @Test
    public void testMissingArgument() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> {
            run("""
                function greet(string name):
                    print name
                greet()
                """);
        });
        assertEquals("Missing argument 'name'.", error.getMessage());
    }

    @Test
    public void testWrongArgumentType() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> {
            run("""
                function add(integer a, integer b):
                    print a + b
                add(a: 10, b: "wrong")
                """);
        });
        assertEquals("Expected integer value but got String.", error.getMessage());
    }

    @Test
    public void testOptionalArguments() {
        run("""
            function greet(string name = "programmer"):
                print "greetings {name}!"
            
            greet()
            greet(name: "Junie")
            """);
        assertOutput("greetings programmer!\ngreetings Junie!");
    }

    @Test
    public void testUnexpectedArgument() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> {
            run("""
                function sayHello():
                    print "hello"
                sayHello(extra: "unexpected")
                """);
        });
        assertEquals("Unexpected argument 'extra'.", error.getMessage());
    }

    @Test
    public void testDuplicateArgumentInCall() {
        CompileError error = assertThrows(CompileError.class, () -> {
            run("""
                function greet(string name):
                    print name
                greet(name: "Junie", name: "Duplicate")
                """);
        });
        assertEquals("Duplicate argument 'name'.", error.getMessage());
    }

    @Test
    public void testPositionalArgumentsError() {
        CompileError error = assertThrows(CompileError.class, () -> {
            run("""
                function add(integer a, integer b):
                    print a + b
                add(10, 20)
                """);
        });
        assertEquals("Expect argument name.", error.getMessage());
    }

    @Test
    public void testDefaultValueTypeMismatch() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> {
            run("""
                function add(integer a, integer b = "wrong"):
                    print a + b
                add(a: 10)
                """);
        });
        assertEquals("Expected integer value but got String.", error.getMessage());
    }
}
