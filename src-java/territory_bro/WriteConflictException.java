// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

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
