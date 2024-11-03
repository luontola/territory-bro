package territory_bro;

public class WriteConflictException extends RuntimeException {

    public WriteConflictException() {
    }

    public WriteConflictException(String message) {
        super(message);
    }

    public WriteConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    public WriteConflictException(Throwable cause) {
        super(cause);
    }
}
