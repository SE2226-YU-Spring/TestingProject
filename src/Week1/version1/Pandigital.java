package Week1.version1;


import java.util.Scanner;

/*
  A straightforward implementation for the given task, even though it works well for the integers, is not "complete"
  due to some properties, which will be explained in the next versions. And the written code itself is
  hard to debug and test since the code is written as a single function with values directly taken from the user.
 */
public class Pandigital {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);

        System.out.println("Please enter an integer number");
        int number = input.nextInt();

        boolean isPrime = true;

        for (int i = 3; i < number; i = i + 2) {
            if (number % i == 0) {
                isPrime = false;
                break;
            }
        }
        isPrime = isPrime && ((number > 1) && (number % 2 != 0)) || (number == 2);

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
        boolean isPandigital = isPrime && isUnique;
        String answer = (isPandigital) ? "pandigital" : "not pandigital";
        System.out.println("The number is " + answer);
    }
}
