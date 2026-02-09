package Week2;

public enum CalculatorError {
    NegativeNumberError, ObjectTypeError;

    @Override
    public String toString() {
        String word = null;
        if (this == NegativeNumberError) {
            word = "NegativeNumberError";
        } else if (this == ObjectTypeError) {
            word = "ObjectTypeError";
        } else {
            throw new IllegalArgumentException("Undefined Key");
        }
        return word;
    }
}
