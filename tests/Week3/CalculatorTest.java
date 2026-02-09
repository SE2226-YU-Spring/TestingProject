package Week3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import week2.Calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CalculatorTest {

    @ParameterizedTest
    @ValueSource(ints = {5,17,38,48,80,41,67,90,32,127 })
    void onlyPositiveIntegers(int value) {
        assertEquals(Integer.toBinaryString(value), Calculator.toBinaryString(value));
    }
}