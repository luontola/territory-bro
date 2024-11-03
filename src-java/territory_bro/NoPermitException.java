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
