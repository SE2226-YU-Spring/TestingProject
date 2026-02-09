package Week1.version3;

/*
    A Test class to give human-readable feedback to the programmer about the test results.
    If a test fails, the programmer can also read the related information about the failed test, such as
    the provided input and the expected real output. The "printOutput" method is overloaded to handle different
    types of parameters with various functions.
 */
public class PandigitalTest {

    public static int TEST_NUMBER = 1;

    public void testIsUnique(boolean expectedValue, int number) {
        System.out.print("Test : " + TEST_NUMBER + " Function : isUnique");
        boolean actualValue = Pandigital.isUnique(number);
        if (actualValue == expectedValue) {
            System.out.println(" Passed");
        } else {
            System.out.print(" Failed ");
            System.out.println(" With inputs : " + number);
            System.out.println("Expected Value : " + expectedValue + " Actual Value : " + actualValue);
        }
        TEST_NUMBER++;
    }

    public void testIsPrime(boolean expectedValue, int number) {
        System.out.print("Test : " + TEST_NUMBER + " Function : isPrime");
        boolean actualValue = Pandigital.isPrime(number);
        if (actualValue == expectedValue) {
            System.out.println(" Passed");
        } else {
            System.out.print(" Failed ");
            System.out.println(" With inputs : " + number);
            System.out.println("Expected Value : " + expectedValue + " Actual Value : " + actualValue);
        }
        TEST_NUMBER++;
    }

    public void testIsValidInput(boolean expectedValue, String input) {
        System.out.print("Test : " + TEST_NUMBER + " Function : isValidInput");
        boolean actualValue = Pandigital.isValidInput(input);
        if (actualValue == expectedValue) {
            System.out.println(" Passed");
        } else {
            System.out.print(" Failed ");
            System.out.println(" With inputs : " + input);
            System.out.println("Expected Value : " + expectedValue + " Actual Value : " + actualValue);
        }
        TEST_NUMBER++;
    }

    public void testIsPandigital(boolean expectedValue, String input) {
        System.out.print("Test : " + TEST_NUMBER + " Function : isPandigital");
        boolean actualValue = Pandigital.isPandigital(input);
        if (actualValue == expectedValue) {
            System.out.println(" Passed");
        } else {
            System.out.print(" Failed ");
            System.out.println(" With inputs : " + input);
            System.out.println("Expected Value : " + expectedValue + " Actual Value : " + actualValue);
        }
        TEST_NUMBER++;
    }

}
