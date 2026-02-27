public class Example {

    public void sayHello(int count) {
        // A simple method to test MethodDeclaration
        if (count > 0) {
            // An IfStmt to test your conditional detection
            System.out.println("Hello World " + count + " times!");
        } else {
            System.out.println("No greetings today.");
        }
    }

    public int calculateSum(int a, int b) {
        int result = a + b;

        for (int i = 0; i < 5; i++) {
            // A ForStmt to see how your 'walk' handles loops
            result += i;
        }

        return result;
    }

    public void testMethod() {
        System.out.println("This is a test method.");
    }
}