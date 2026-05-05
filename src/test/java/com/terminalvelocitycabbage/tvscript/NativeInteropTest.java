package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.execution.Environment;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.execution.NativeClass;
import com.terminalvelocitycabbage.tvscript.execution.TVType;
import com.terminalvelocitycabbage.tvscript.stdlib.NativeFunctions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeInteropTest {

    private static final class Vector2d {
        static final Vector2d UNIT_VECTOR = new Vector2d(1, 0);

        private int x;
        private int y;

        private Vector2d(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public void setX(int x) {
            this.x = x;
        }

        public void setY(int y) {
            this.y = y;
        }

        public Vector2d add(Vector2d delta) {
            return new Vector2d(x + delta.x, y + delta.y);
        }

        public Vector2d identity() {
            return this;
        }
    }

    private static final class VectorBox {
        private Vector2d value;

        private VectorBox(Vector2d value) {
            this.value = value;
        }

        public Vector2d getValue() {
            return value;
        }

        public void setValue(Vector2d value) {
            this.value = value;
        }
    }

    private static final NativeClass VEC2 = NativeClass.builder("Vec2", Vector2d.class)
            .constructor(
                    NativeClass.params(
                            NativeClass.param("x", TVType.INTEGER),
                            NativeClass.param("y", TVType.INTEGER)
                    ),
                    args -> new Vector2d(
                            (int) args.get("x"),
                            (int) args.get("y")
                    )
            )
            .property("x", TVType.INTEGER, Vector2d::getX, Vector2d::setX)
            .property("y", TVType.INTEGER, Vector2d::getY, Vector2d::setY)
            .constant("UNIT_VECTOR", TVType.self(), Vector2d.UNIT_VECTOR)
            .method(
                    "add",
                    NativeClass.params(NativeClass.param("delta", TVType.self())),
                    TVType.self(),
                    (self, args) -> self.add((Vector2d) args.get("delta"))
            )
            .method(
                    "identity",
                    NativeClass.params(),
                    TVType.self(),
                    (self, args) -> self.identity()
            )
            .build();

    private static final NativeClass VEC_BOX = NativeClass.builder("VecBox", VectorBox.class)
            .constructor(
                    NativeClass.params(NativeClass.param("value", TVType.ref(VEC2))),
                    args -> new VectorBox((Vector2d) args.get("value"))
            )
            .property("value", TVType.ref(VEC2), VectorBox::getValue, VectorBox::setValue)
            .build();

    private static Environment globalEnvironment;

    private Interpreter interpreter;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeAll
    public static void setupGlobalEnvironment() {
        globalEnvironment = new Environment.GlobalBuilder()
                .withNativeFunctions(NativeFunctions.getAll())
                .withClass(VEC_BOX)
                .withClass(VEC2)
                .build();
    }

    @BeforeEach
    public void setUp() {
        interpreter = new Interpreter(globalEnvironment);
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
    }

    private void run(String source) {
        TVScript.run(source, interpreter);
    }

    private void assertOutput(String expected) {
        assertEquals(expected.trim(), outContent.toString().replace("\r\n", "\n").trim());
    }

    @Test
    public void testEnvironmentBuildResolvesSelfAndCrossReferencesWithoutOrdering() {
        assertNotNull(globalEnvironment.getNativeClass("Vec2"));
        assertNotNull(globalEnvironment.getNativeClass("VecBox"));
    }

    @Test
    public void testEnvironmentBuildFailsFastOnDuplicateNativeClassNames() {
        NativeClass duplicate = NativeClass.builder("Vec2", VectorBox.class)
                .constructor(
                        NativeClass.params(NativeClass.param("value", TVType.ref(VEC2))),
                        args -> new VectorBox((Vector2d) args.get("value"))
                )
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new Environment.GlobalBuilder()
                        .withClass(VEC2)
                        .withClass(duplicate)
                        .build()
        );

        assertTrue(error.getMessage().contains("Vec2"));
        assertTrue(error.getMessage().contains(Vector2d.class.getSimpleName()));
        assertTrue(error.getMessage().contains(VectorBox.class.getSimpleName()));
    }

    @Test
    public void testEnvironmentBuildFailsOnUnknownNativeReference() {
        NativeClass unresolved = NativeClass.builder("BrokenBox", VectorBox.class)
                .constructor(
                        NativeClass.params(NativeClass.param("value", TVType.ref(VEC2))),
                        args -> new VectorBox((Vector2d) args.get("value"))
                )
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new Environment.GlobalBuilder()
                        .withClass(unresolved)
                        .build()
        );

        assertTrue(error.getMessage().contains("Vec2"));
    }

    @Test
    public void testNativeClassBindingRequiresRegisteredNativeType() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            native class Missing:
                pass
            """));

        assertEquals("'Missing' not defined as a native class type on the global environment.", error.getMessage());
    }

    @Test
    public void testNativeClassDoesNotAllowInstanceFields() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            native class Vec2:
                integer x
            """));

        assertEquals("Native classes cannot declare instance fields.", error.getMessage());
    }

    @Test
    public void testNativeClassDoesNotAllowConstructors() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            native class Vec2:
                constructor(integer x, integer y):
                    pass
            """));

        assertEquals("Native classes cannot declare constructors.", error.getMessage());
    }

    @Test
    public void testNativeClassDoesNotAllowNativeMethodOverrides() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            native class Vec2:
                add(Vec2 delta) -> Vec2:
                    return this
            """));

        assertEquals("Native class 'Vec2' cannot override native member 'add'.", error.getMessage());
    }

    @Test
    public void testNativeClassCanAddMethodsFunctionsAndClassConstants() {
        run("""
            native class Vec2:
                const integer SCALE = 2

                function fromX(integer x) -> Vec2:
                    return new Vec2(x: x, y: 0)

                subtract(Vec2 delta) -> Vec2:
                    return new Vec2(x: this.x - delta.x, y: this.y - delta.y)

            Vec2 a = new Vec2(x: 10, y: 5)
            Vec2 b = new Vec2(x: 2, y: 1)
            Vec2 c = a.subtract(delta: b)
            Vec2 d = a.add(delta: b)

            print c.x
            print c.y
            print d.x
            print d.y
            print Vec2.SCALE

            Vec2 unit = Vec2.fromX(x: 7)
            print unit.x
            print unit.y
            """);

        assertOutput("""
                8
                4
                12
                6
                2
                7
                0
                """);
    }

    @Test
    public void testNativeWrapperIdentityMatchesUnderlyingJavaIdentity() {
        run("""
            native class Vec2:
                pass

            Vec2 a = new Vec2(x: 3, y: 4)
            Vec2 b = a.identity()
            print a == b
            """);

        assertOutput("true");
    }
}