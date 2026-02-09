package Week1.version4;

import java.util.ArrayList;

/*
    A Test class to give human-readable feedback to the programmer about the test results.
    If a test fails, the programmer can also read the related information about the failed test, such as
    the provided input and the expected real output. The "printOutput" method is overloaded to handle different
    types of parameters with various functions.
 */
public class PandigitalTest {

    public static int TEST_NUMBER = 1;

    public void testIsUnique(boolean expectedValue, int number) {
        boolean actualValue = Pandigital.isUnique(number);
        printOutput("isUnique", expectedValue, actualValue, number);
    }

    public void testIsPrime(boolean expectedValue, int number) {
        boolean actualValue = Pandigital.isPrime(number);
        printOutput("isPrime", expectedValue, actualValue, number);
    }

    public void testIsValidInput(boolean expectedValue, String input) {
        boolean actualValue = Pandigital.isValidInput(input);
        printOutput("isValidInput", expectedValue, actualValue, input);
    }

    public void testIsPandigital(boolean expectedValue, Object input) {
        boolean actualValue = Pandigital.isPandigital(input);
        printOutput("isPandigital", expectedValue, actualValue, input);
    }

    private static void printOutput(String functionName, Object expectedValue, Object actualValue, Object input) {
        System.out.print("Test : " + TEST_NUMBER + " Function : " + functionName);
        if (expectedValue.equals(actualValue)) {
            System.out.println(" Passed");
        } else {
            System.out.print(" Failed ");
            System.out.println(" With input : " + input);
            System.out.println("Expected Value : " + expectedValue + " Actual Value : " + actualValue);
        }
        TEST_NUMBER++;
    }

}
