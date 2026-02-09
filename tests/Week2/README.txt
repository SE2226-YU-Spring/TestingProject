Inspect the code inside week1 from version1 to version4.
After you inspect the other classes in this package, you can try to perform the same testing operations
using the JUnit library. To be able to that, first, you have to specify a package to write your tests.
If you are using IntelliJ, you can do that by following the steps below:

* Create a new package named "tests" in the ROOT folder.
* Right-click to "tests" and navigate to "Mark Directory as " then select "Test Source Root"
* Open the week1/version4/Pandigital Class
* Right-Click on the file and select "Generate"
* Select "Test..."
* It will prompt a new screen with an explanation on top saying "JUnit5 library not found in this module"
* Click "Fix"
* Change the line in the search bar to "org.junit.jupiter:junit-jupiter:5.10.1" for the latest version and click OK
* !! TYPE "week1" TO DESTINATION PACKAGE then, select all the functions named under "Member" and click OK !!
* It will open a new class with "PandigitalTest" which you can also find in tests/week1/PandigitalTest

!! Replace the lines with the lines given in below. !!
!! Import necessary packages as well !!

class PandigitalTest {


    static Random rand;

    @BeforeAll
    @DisplayName("Init Random")
    static void createRandom() {
        System.out.println("This method run before all other tests");
        rand = new Random();
    }

    @Test
    void isPrime() {
        assertTrue(Pandigital.isPrime(2));
        assertFalse(Pandigital.isPrime(6));
    }

    @Test
    void isUnique() {
    }

    @Test
    void isValidInput() {
    }

    @Test
    void isPandigital() {
    }
}

* Right Click to file and Run "PandigitalTest"

Explanation about the annotation tags in "PandigitalTest" will be shown in the week2 lab session.
Please refer to the "std.txt" under the "README.txt" for the next week's lab assignment.


