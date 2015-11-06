(ns territory-bro.util
  (:import (java.sql SQLException)))

(defn fix-sqlexception-chain [e]
  (when (instance? SQLException e)
    (if-let [next (.getNextException e)]
      (if (.getCause e)
        (.addSuppressed e next)
        (.initCause e next))))
  ; XXX: Will recurse through getNextException chain because it is returned by getCause.
  ; FIXME: Will not recurse through getNextException chain if there already was a cause and the chain was suppressed
  (when (instance? Throwable e)
    (fix-sqlexception-chain (.getCause e)))
  e)
