package Week1.version2;


import java.util.Scanner;

/*
  A more modular approach for the same task where the flow of the program is divided into different functions
  which let the programmer test each process in a more isolated matter to detect bugs, still, a single class
  is responsible for every task on the problem that conflicts with the S.O.L.I.D principles.
 */

public class Pandigital {

    public static boolean isPrime(int number){
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

    public static boolean isUnique(int number){
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

    public static boolean isValidInput(String input){
        boolean isValid = false;
        try{
            int number = Integer.parseInt(input);
            isValid = (number > 0);
        }catch (Exception e){
            System.out.println("The input value is not valid");
            isValid = false;
        }

        return isValid;
    }

    public static void isPandigital(String input){
        if(isValidInput(input)){
            int number = Integer.parseInt(input);
            boolean isPrime = isPrime(number);
            boolean isUnique = isUnique(number);
            boolean isPandigital = isPrime && isUnique;
            String answer = (isPandigital) ? "pandigital" : "not pandigital";
            System.out.println("The number is " + answer);
        }
    }


    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        System.out.println("Please enter an integer number");
        String userInput = input.next();
        isPandigital(userInput);
    }
}
