// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package territory_bro;

import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import clojure.lang.Sequential;

@SuppressWarnings("unused")
public class ValidationException extends RuntimeException {

    private final Sequential errors;

    public ValidationException(IPersistentVector errors) {
        super(errors.toString());
        validateErrors(errors);
        this.errors = errors;
    }

    private static void validateErrors(IPersistentVector errors) {
        if (errors.length() == 0) {
            throw new IllegalArgumentException("Need at least one error: " + errors);
        }
        for (int i = 0; i < errors.length(); i++) {
            Object error = errors.nth(i);
            if (error instanceof IPersistentVector) {
                Object first = ((IPersistentVector) error).nth(0);
                if (first instanceof Keyword) {
                    continue;
                }
            }
            throw new IllegalArgumentException("Each error must be a vector which starts with a keyword: " + errors);
        }
    }

    public Sequential getErrors() {
        return errors;
    }
}
