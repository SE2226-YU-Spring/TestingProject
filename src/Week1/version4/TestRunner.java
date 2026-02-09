package Week1.version4;

/*
    A Test Runner Class uses the functions in other classes as a whole or individually to detect bugs, errors, etc.
    However, due to the implementation of the "Triangle" class, "the completeness" problem occurs because the program
    "can only handle the integers" however, in reality, the user can provide any input he/she wants. Therefore, all
    the classes written in this manner should handle the "generic" type of information. In java, we will refer to this
    type of inputs as "Object" and to be able to provide any number of them, we will use the "ArrayList<Object> or Object"
    structure.
 */
public class TestRunner {
    public static void main(String[] args) {
        // You can add the remaining tests by yourself
        PandigitalTest tester = new PandigitalTest();
        tester.testIsPandigital(true,2143);

        tester.testIsPandigital(false,null);
        tester.testIsPandigital(false,'a');
        tester.testIsPandigital(false,"ATA");
        tester.testIsPandigital(false,"2143");
        tester.testIsPandigital(false,2143.3);
        tester.testIsPandigital(false,-2143);


    }
}

