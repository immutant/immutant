(ns fnbox.test.helpers
  (:use clojure.test))

(defmacro is-not
  ;; NOTE: this won't work with any assert-expr's
  ([form] `(is-not ~form nil))
  ([form msg] `(try-expr ~msg (not ~form))))

(defmethod assert-expr 'not-thrown? [msg form]
  ;; (is (not-thrown? c expr))
  ;; Asserts that evaluating expr does not throw an exception of class c.
  (let [klass (second form)
        body (nthnext form 2)]
    `(try ~@body
          (do-report {:type :pass, :message ~msg,
                   :expected '~form, :actual '~form})
          (catch ~klass e#
            (do-report {:type :fail, :message ~msg,
                     :expected '~form, :actual e#})
            e#))))
