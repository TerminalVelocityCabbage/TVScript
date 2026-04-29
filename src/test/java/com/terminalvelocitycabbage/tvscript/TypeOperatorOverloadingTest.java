package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Environment;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.stdlib.NativeFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeOperatorOverloadingTest {

    private Interpreter interpreter;
    private static Environment globalEnvironment;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @BeforeAll
    public static void setupGlobalEnvironment() {
        globalEnvironment = new Environment.GlobalBuilder()
                .withNativeFunctions(NativeFunctions.getAll())
                .build();
    }

    @BeforeEach
    public void setUp() {
        interpreter = new Interpreter(globalEnvironment);
        System.setOut(new PrintStream(outContent));
    }

    private void run(String source) {
        TVScript.run(source, interpreter);
    }

    private void assertOutput(String expected) {
        assertEquals(expected.trim(), outContent.toString().replace("\r\n", "\n").trim());
    }

    @Test
    public void testTypeConstructionAndFieldAccess() {
        run("""
            type vector2d:
                decimal x
                decimal y

            vector2d v = new vector2d(x: 10.5, y: 2.25)
            print v.x
            print v.y
            """);

        assertOutput("""
            10.5
            2.25
            """);
    }

    @Test
    public void testAddOperatorOverloadWithExplicitTypes() {
        run("""
            type vector2d:
                decimal x
                decimal y

                operator add(vector2d left, vector2d right) -> vector2d:
                    return new vector2d(x: left.x + right.x, y: left.y + right.y)

            vector2d v1 = new vector2d(x: 10.0, y: 5.0)
            vector2d v2 = new vector2d(x: 1.5, y: 2.5)
            vector2d sum = v1 + v2
            print sum.x
            print sum.y
            """);

        assertOutput("""
            11.5
            7.5
            """);
    }

    @Test
    public void testSubtractOperatorOverloadWithInferredSignature() {
        run("""
            type vector2d:
                decimal x
                decimal y

                operator subtract(left, right):
                    return new vector2d(x: left.x - right.x, y: left.y - right.y)

            vector2d v1 = new vector2d(x: 10.0, y: 5.0)
            vector2d v2 = new vector2d(x: 1.5, y: 2.5)
            vector2d diff = v1 - v2
            print diff.x
            print diff.y
            """);

        assertOutput("""
            8.5
            2.5
            """);
    }

    @Test
    public void testMultiplyOperatorOverloadWithDifferentOperandType() {
        run("""
            type vector2d:
                decimal x
                decimal y

                operator multiply(vector2d left, decimal right) -> vector2d:
                    return new vector2d(x: left.x * right, y: left.y * right)

            vector2d v = new vector2d(x: 2.0, y: 3.0)
            vector2d scaled = v * 2.5
            print scaled.x
            print scaled.y
            """);

        assertOutput("""
            5.0
            7.5
            """);
    }

    @Test
    public void testDivideOperatorOverloadWithDifferentOperandType() {
        run("""
            type vector2d:
                decimal x
                decimal y

                operator divide(vector2d left, decimal right) -> vector2d:
                    return new vector2d(x: left.x / right, y: left.y / right)

            vector2d v = new vector2d(x: 10.0, y: 5.0)
            vector2d scaled = v / 2.0
            print scaled.x
            print scaled.y
            """);

        assertOutput("""
            5.0
            2.5
            """);
    }

    @Test
    public void testModuloOperatorOverload() {
        run("""
            type wrapIndex:
                integer value

                operator modulo(wrapIndex left, integer right) -> integer:
                    return left.value % right

            wrapIndex index = new wrapIndex(value: 17)
            var wrapped = index % 5
            print wrapped
            """);

        assertOutput("""
            2
            """);
    }

    @Test
    public void testUnaryNegativeOperatorOverload() {
        run("""
            type vector2d:
                decimal x
                decimal y

                operator negative(vector2d right) -> vector2d:
                    return new vector2d(x: -right.x, y: -right.y)

            vector2d v = new vector2d(x: 2.0, y: -3.0)
            vector2d inverted = -v
            print inverted.x
            print inverted.y
            """);

        assertOutput("""
            -2.0
            3.0
            """);
    }

    @Test
    public void testCompareOperatorOverloadDrivesEqualityAndOrdering() {
        run("""
            type score:
                decimal value

                operator compare(score left, score right) -> decimal:
                    return left.value - right.value

            score a = new score(value: 2.5)
            score b = new score(value: 2.75)
            score c = new score(value: 2.5)

            print a < b
            print b > a
            print a == c
            print a != b
            print a <= c
            print b >= c
            """);

        assertOutput("""
            true
            true
            true
            true
            true
            true
            """);
    }

    @Test
    public void testOperatorCanReturnDifferentTypeThanTypeBeingOverloaded() {
        run("""
            type vector2d:
                decimal x
                decimal y

                operator multiply(vector2d left, vector2d right) -> decimal:
                    return left.x * right.x + left.y * right.y

            vector2d v1 = new vector2d(x: 2.0, y: 3.0)
            vector2d v2 = new vector2d(x: 4.0, y: 5.0)
            decimal dot = v1 * v2
            print dot
            """);

        assertOutput("""
            23.0
            """);
    }

    @Test
    public void testOperatorOverloadSupportsSubclassArgumentType() {
        run("""
            class entity:
                constructor():
                    pass

            class player < entity:
                constructor():
                    pass

            type vector2d:
                decimal x
                decimal y

                operator add(vector2d left, entity right) -> decimal:
                    return left.x + left.y

            vector2d v = new vector2d(x: 2.0, y: 3.0)
            player p = new player()
            decimal value = v + p
            print value
            """);

        assertOutput("""
            5.0
            """);
    }

    @Test
    public void testMissingOperatorOverloadThrowsElegantRuntimeError() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> run("""
            type vector2d:
                decimal x
                decimal y

            vector2d v1 = new vector2d(x: 10.0, y: 5.0)
            vector2d v2 = new vector2d(x: 1.0, y: 2.0)
            vector2d result = v1 / v2
            """));

        assertTrue(error.getMessage().contains("no operator overload defined for \"divide\" between vector2d and vector2d"));
    }

    @Test
    public void testTypeFieldsAreImmutableCompileError() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            type vector2d:
                decimal x
                decimal y

            vector2d v = new vector2d(x: 1.0, y: 2.0)
            v.x = 10.0
            """));

        assertTrue(error.getMessage().contains("Type fields are immutable"));
    }

    @Test
    public void testOperatorParamsMustBeNamedLeftAndRightWhenSpecified() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            type vector2d:
                decimal x
                decimal y

                operator add(vector2d a, vector2d b) -> vector2d:
                    return new vector2d(x: a.x + b.x, y: a.y + b.y)
            """));

        assertTrue(error.getMessage().contains("Operator parameters must be named left and right"));
    }

    @Test
    public void testComparisonsRequireCompareOperator() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> run("""
            type point:
                integer x

            point a = new point(x: 1)
            point b = new point(x: 1)
            print a == b
            """));

        assertTrue(error.getMessage().contains("no operator overload defined for \"comparison\" between point and point"));
    }

    @Test
    public void testUnsupportedOperatorNameProducesCompileError() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            type vector2d:
                decimal x
                decimal y

                operator equal(vector2d left, vector2d right) -> boolean:
                    return true
            """));

        assertTrue(error.getMessage().contains("Unsupported operator overload 'equal'"));
    }
}