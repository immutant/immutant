;;; Monkey-patchery to prevent calls to setAutoCommit/commit/rollback on connection
(in-ns 'clojure.java.jdbc)
(use '[clojure.java.jdbc.internal :only [transaction*]])
(def ^{:dynamic true} *tx-strategy* @(resolve 'transaction*))
(intern 'clojure.java.jdbc.internal 'transaction* (fn [& args] (apply *tx-strategy* args)))
(defmacro with-transaction-strategy
  [strategy & body]
  `(binding [*tx-strategy* ~strategy] ~@body))
