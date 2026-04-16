package com.terminalvelocitycabbage.tvscript;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.analysis.TypeChecker;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;

import static org.junit.jupiter.api.Assertions.*;

class ClassTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    private void run(String source) {
        TVScript.hadError = false;
        TVScript.hadRuntimeError = false;

        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parseStatements();
        if (TVScript.hadError) throw new RuntimeException("Parse error");

        TypeChecker typeChecker = new TypeChecker();
        typeChecker.check(statements);
        if (TVScript.hadError) throw new RuntimeException("Type check error");

        Interpreter interpreter = new Interpreter();
        interpreter.interpret(statements);
    }

    @Test
    void testSimpleClass() {
        run("""
            class Player:
                string name
                constructor(string name):
                    this.name = name
            
            Player player = new Player(name: "Junie")
            print player.name
            """);
        assertEquals("Junie\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testClassWithMethods() {
        run("""
            class Greeter:
                string greeting
                constructor(string greeting):
                    this.greeting = greeting
                
                greet(string name):
                    print "{this.greeting}, {name}!"
            
            Greeter greeter = new Greeter(greeting: "Hello")
            greeter.greet(name: "World")
            """);
        assertEquals("Hello, World!\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testClassDefaultValues() {
        run("""
            class Point:
                integer x = 0
                integer y = 0
                constructor(integer x, integer y):
                    this.x = x
                    this.y = y
            
            Point p = new Point(x: 10, y: 20)
            print p.x
            print p.y
            """);
        assertEquals("10\n20\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testClassDefaultValuesFallback() {
        run("""
            class Point:
                integer x = 0
                integer y = 10
                constructor():
                    pass
            
            Point p = new Point()
            print p.x
            print p.y
            """);
        assertEquals("0\n10\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testMissingConstructorError() {
        // "If no constructor is defined for a class this is a compile error."
        assertThrows(RuntimeException.class, () -> run("""
            class NoConstructor:
                string field
            """));
    }

    @Test
    void testThisOutsideMethodError() {
        assertThrows(RuntimeException.class, () -> run("""
            print this
            """));
        
        assertThrows(RuntimeException.class, () -> run("""
            function standalone():
                print this
            """));

        assertThrows(RuntimeException.class, () -> run("""
            class Player:
                constructor(): pass
                
                function testThis():
                    print this
            """));
    }

    @Test
    void testNamedArgumentsOnly() {
        // Position arguments are not supported
        assertThrows(RuntimeException.class, () -> run("""
            class A:
                constructor(integer x):
                    pass
            A a = new A(10)
            """));
    }

    @Test
    void testStaticMethods() {
        run("""
            class MathUtils:
                constructor(): pass
                
                function add(integer a, integer b) -> integer:
                    return a + b
            
            print MathUtils.add(a: 10, b: 20)
            """);
        assertEquals("30\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testMultipleConstructors() {
        run("""
            class Player:
                string name
                integer score
                
                constructor(string name):
                    this.name = name
                    this.score = 0
                
                constructor(string name, integer score):
                    this.name = name
                    this.score = score
            
            Player p1 = new Player(name: "Junie")
            print p1.name
            print p1.score
            
            Player p2 = new Player(name: "Robot", score: 100)
            print p2.name
            print p2.score
            """);
        assertEquals("Junie\n0\nRobot\n100\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testConstructorOverloadingWithDefaultValues() {
        run("""
            class Point:
                integer x
                integer y
                
                constructor(integer x):
                    this.x = x
                    this.y = 0
                
                constructor(integer x, integer y = 10):
                    this.x = x
                    this.y = y
            
            Point p1 = new Point(x: 1)
            print p1.x
            print p1.y
            
            Point p2 = new Point(x: 2, y: 20)
            print p2.x
            print p2.y
            """);
        // For new Point(x: 1), both match.
        // constructor(integer x) has 1 param, 0 defaults.
        // constructor(integer x, integer y = 10) has 2 params, 1 default.
        // Our selection logic picks the one with fewer unused parameters.
        // unusedParams = arity - arguments.size
        // C1: 1 - 1 = 0
        // C2: 2 - 1 = 1
        // C1 is picked.
        assertEquals("1\n0\n2\n20\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testValueMutation() {
        // Primitive types are passed by value
        run("""
            class Container:
                integer value
                constructor(integer value):
                    this.value = value
            
            integer x = 10
            Container c = new Container(value: x)
            print c.value
            x = 20
            print c.value
            print x
            """);
        assertEquals("10\n10\n20\n", outContent.toString().replace("\r\n", "\n"));
        outContent.reset();

        // Objects are passed by reference (the reference is passed by value)
        run("""
            class Value:
                integer val
                constructor(integer val):
                    this.val = val
            
            class Holder:
                Value v
                constructor(Value v):
                    this.v = v
            
            Value v1 = new Value(val: 1)
            Holder h = new Holder(v: v1)
            print h.v.val
            v1.val = 2
            print h.v.val
            
            v1 = new Value(val: 3)
            print h.v.val
            print v1.val

            // Multiple objects pointing to the same object
            Value shared = new Value(val: 100)
            Holder h1 = new Holder(v: shared)
            Holder h2 = new Holder(v: shared)
            print h1.v.val
            print h2.v.val
            shared.val = 200
            print h1.v.val
            print h2.v.val
            h1.v = new Value(val: 300)
            print h1.v.val
            print h2.v.val
            print shared.val
            """);
        assertEquals("1\n2\n2\n3\n100\n100\n200\n200\n300\n200\n200\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testFieldAssignmentMutation() {
        run("""
            class Box:
                integer val
                constructor():
                    this.val = 0
            
            Box b = new Box()
            integer i = 5
            b.val = i
            i = 10
            print b.val
            print i
            """);
        assertEquals("5\n10\n", outContent.toString().replace("\r\n", "\n"));
        outContent.reset();

        run("""
            class Inner:
                integer val
                constructor(integer val): this.val = val
            class Outer:
                Inner inner
                constructor(Inner inner): this.inner = inner
            
            Inner in1 = new Inner(val: 100)
            Outer out = new Outer(inner: in1)
            
            Inner in2 = new Inner(val: 200)
            out.inner = in2
            
            in2.val = 300
            print out.inner.val
            
            in2 = new Inner(val: 400)
            print out.inner.val
            """);
        assertEquals("300\n300\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testReadmePassByReferenceExample() {
        run("""
            class Counter:
                integer count = 0
                constructor(): pass

            function incrementCounter(Counter c):
                c.count = c.count + 1

            function reassignCounter(Counter c):
                c = new Counter()
                c.count = 10

            Counter myCounter = new Counter()
            incrementCounter(c: myCounter)
            print myCounter.count // prints 1

            reassignCounter(c: myCounter)
            print myCounter.count // still prints 1
            """);
        assertEquals("1\n1\n", outContent.toString().replace("\r\n", "\n"));
    }
}
