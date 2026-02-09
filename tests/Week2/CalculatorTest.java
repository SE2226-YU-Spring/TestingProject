package Week2;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;


import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class CalculatorTest {
    static Random rand;

    @BeforeAll
    static void initRandom() {
        rand = new Random();
    }

    @Test
    void add() {
        assertEquals(8, Calculator.add(3, 5));
    }

    @Test
    void divide() {
        //divide function without any error handling
        assertEquals(2, Calculator.divide(4, 2));

        //Use this test if Java, throws error automatically.
        Exception exception = assertThrows(ArithmeticException.class, () -> {
            Calculator.divideDefault(5, 0);
        });
        assertEquals("/ by zero", exception.getMessage());

        //Use this test if your function throws right Exception
        assertThrows(ArithmeticException.class, () -> {
            Calculator.divideManual(5, 0);
        });

        //Use this test if you are catching error
        assertEquals(0, Calculator.divideCatch(5, 0));
    }

    @Test
    void toBinaryString() {
        assertEquals(CalculatorError.ObjectTypeError.toString(), Calculator.toBinaryString('g'));
        assertEquals(CalculatorError.NegativeNumberError.toString(), Calculator.toBinaryString(-5));
        assertEquals("0", Calculator.toBinaryString(0));
    }

    @RepeatedTest(100)
    void repeatedToBinaryString() {
        int value = rand.nextInt(500);
        assertEquals(Integer.toBinaryString(value), Calculator.toBinaryString(value));
    }
}