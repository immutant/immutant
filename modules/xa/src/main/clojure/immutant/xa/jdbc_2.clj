;;; Monkey-patchery to prevent calls to setAutoCommit/commit/rollback on connection
(in-ns 'clojure.java.jdbc)
(def ^{:dynamic true} *tx-strategy* transaction*)
(defn transaction* [& args] (apply *tx-strategy* args))
(defmacro with-transaction-strategy
  [strategy & body]
  `(binding [*tx-strategy* ~strategy] ~@body))
