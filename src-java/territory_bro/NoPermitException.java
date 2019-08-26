// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package territory_bro;

import clojure.lang.IPersistentVector;

import java.util.UUID;

@SuppressWarnings("unused")
public class NoPermitException extends RuntimeException {

    private final UUID userId;
    private final IPersistentVector permit;

    public NoPermitException(UUID userId, IPersistentVector permit) {
        super("User " + userId + " does not have permit " + permit);
        this.userId = userId;
        this.permit = permit;
    }

    public UUID getUserId() {
        return userId;
    }

    public IPersistentVector getPermit() {
        return permit;
    }
}
