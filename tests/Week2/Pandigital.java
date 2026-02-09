package Week2;


/*
    A Pandigital class responsible for carrying out necessary functionalities to be able to perform the given task.
 */

public class Pandigital {

    public static boolean isPrime(int number) {
        boolean isPrime = true;

        for (int i = 3; i < number; i = i + 2) {
            if (number % i == 0) {
                isPrime = false;
                break;
            }
        }
        isPrime = isPrime && ((number > 1) && (number % 2 != 0)) || (number == 2);

        return isPrime;
    }

    public static boolean isUnique(int number) {
        boolean isUnique = true;
        boolean[] isExist = new boolean[10];
        int length = 0;
        while (number > 0) {
            int lastDigit = number % 10;
            number = number / 10;
            length++;

            if (isExist[lastDigit]) {
                isUnique = false;
            } else {
                isExist[lastDigit] = true;
            }
        }
        if (isUnique) {
            for (int i = 1; i <= length; i++) {
                isUnique = isUnique && isExist[i];
            }
        }

        return isUnique;
    }

    public static boolean isValidInput(Object input) {
        boolean isValid = false;
        try {
            isValid = (input instanceof Integer) && (((int) input) > 0);
        } catch (Exception e) {
            System.out.println("The input value is not valid");
            isValid = false;
        }

        return isValid;
    }

    public static boolean isPandigital(Object input) {
        if (isValidInput(input)) {
            int number = (int) input;
            boolean isPrime = isPrime(number);
            boolean isUnique = isUnique(number);
            boolean isPandigital = isPrime && isUnique;
            String answer = (isPandigital) ? "pandigital" : "not pandigital";
            System.out.println("The number is " + answer);
            return true;
        } else {
            return false;
        }
    }
}
