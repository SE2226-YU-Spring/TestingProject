package Week1.version3;

/*
    A Test Runner Class uses the functions in other classes as a whole or individually to detect bugs, errors, etc.
 */
public class TestRunner {
    public static void main(String[] args) {
        // You can add the remaining tests by yourself
        PandigitalTest tester = new PandigitalTest();

        tester.testIsPrime(true, 2);
        tester.testIsPrime(true, 3);
        tester.testIsPrime(true, 2143);
        tester.testIsPrime(true, 2141);
        tester.testIsPrime(true, 103);
        tester.testIsPrime(true, 97);

        tester.testIsPrime(false, 1);
        tester.testIsPrime(false, 4);
        tester.testIsPrime(false, 2142);
        tester.testIsPrime(false, 2140);
        tester.testIsPrime(false, 106);
        tester.testIsPrime(false, 99);
    }
}
