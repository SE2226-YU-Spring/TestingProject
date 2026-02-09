package Week2;

public class Calculator {
    public static int add(int first_value, int second_value) {
        return first_value + second_value;
    }

    public static int divide(int first_value, int second_value) {
        return first_value / second_value;
    }

    //Java will throw Exception that is specifically defined in "throw" clause
    public static int divideManual(int first_value, int second_value) throws ArithmeticException {
        return first_value / second_value;
    }

    //Java will automatically throw "ArithmeticException"
    public static int divideDefault(int first_value, int second_value) {
        return first_value / second_value;
    }

    //Manually catch error and return something
    public static int divideCatch(int first_value, int second_value) {
        int result;
        try {
            result = first_value / second_value;
        } catch (ArithmeticException e) {
            System.out.println("Error : division by zero ");
            result = 0;
        }
        return result;
    }

    public static String toBinaryString(Object input) {
        return "";
    }

}
