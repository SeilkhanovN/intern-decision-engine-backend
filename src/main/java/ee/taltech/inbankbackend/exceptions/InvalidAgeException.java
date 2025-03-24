package ee.taltech.inbankbackend.exceptions;

/**
 * Exception thrown when a customer's age does not meet the requirements for loan approval.
 */
public class InvalidAgeException extends Exception {
    public InvalidAgeException(String message) {
        super(message);
    }
} 