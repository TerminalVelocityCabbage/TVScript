package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericsTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private void run(String source) {
        TVScript.run(source);
    }

    private String stdout() {
        return outContent.toString().replace("\r\n", "\n");
    }

    @Test
    void testGenericFunctionInferenceAndExplicitArguments() {
        run("""
            function identity[T](T value) -> T:
                return value

            print identity(value: 7)
            print identity[decimal](value: 2.5)
            print identity[string](value: "hello")
            """);

        assertEquals("7\n2.5\nhello\n", stdout());
    }

    @Test
    void testGenericClassInferenceAndExplicitArguments() {
        run("""
            class Box[T]:
                T value

                constructor(T value):
                    this.value = value

                get() -> T:
                    return this.value

            Box[integer] explicitBox = new Box[integer](value: 9)
            Box[string] inferredBox = new Box(value: "tv")

            print explicitBox.get()
            print inferredBox.get()
            """);

        assertEquals("9\ntv\n", stdout());
    }

    @Test
    void testGenericConstraintWithSuperclassAndTrait() {
        run("""
            trait MakesSound:
                makeSound()

            class Animal:
                string name
                constructor(string name):
                    this.name = name

            class Dog < Animal [MakesSound]:
                constructor(string name):
                    super(name: name)

                override makeSound():
                    print "woof"

            function trigger[T < Animal & MakesSound](T animal):
                animal.makeSound()
                print animal.name

            Dog dog = new Dog(name: "Rex")
            trigger(animal: dog)
            """);

        assertEquals("woof\nRex\n", stdout());
    }

    @Test
    void testGenericFunctionTypeArityMismatchIsCompileError() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            function identity[T](T value) -> T:
                return value

            print identity[integer, string](value: 10)
            """));

        assertTrue(error.getMessage().toLowerCase().contains("type argument"));
    }

    @Test
    void testGenericClassConstraintViolationIsCompileError() {
        CompileError error = assertThrows(CompileError.class, () -> run("""
            trait MakesSound:
                makeSound()

            class Animal:
                string name
                constructor(string name):
                    this.name = name

            class Dog < Animal [MakesSound]:
                constructor(string name):
                    super(name: name)

                override makeSound():
                    print "woof"

            class Cat < Animal:
                constructor(string name):
                    super(name: name)

            class Cage[T < Animal & MakesSound]:
                T animal
                constructor(T animal):
                    this.animal = animal

            Cage[Cat] catCage = new Cage[Cat](animal: new Cat(name: "Kitty"))
            """));

        assertTrue(error.getMessage().toLowerCase().contains("constraint"));
    }

    @Test
    void testListGenericRuntimeMutationCheck() {
        RuntimeError error = assertThrows(RuntimeError.class, () -> run("""
            list[integer] values = new list[](1, 2)
            var dynamicValues = values
            dynamicValues.add("bad")
            """));

        assertTrue(error.getMessage().toLowerCase().contains("list"));
        assertTrue(error.getMessage().toLowerCase().contains("integer"));
    }
}