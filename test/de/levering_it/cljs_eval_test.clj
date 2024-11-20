(ns de.levering-it.cljs-eval-test
  (:require [clojure.test :refer :all]
            [de.levering-it.cljs-eval :refer [enable-cljs-eval! cljs-eval cljs-eval! disable-cljs-eval!]]))


(defn cljs-repl [f]
  (enable-cljs-eval!)
  (f)
  ; this does not work, yet
  #_(disable-cljs-eval!)
)

; Here we register my-test-fixture to be called once, wrapping ALL tests
; in the namespace
(use-fixtures :once cljs-repl)

(deftest test-eval

  (testing "cljs-eval"
    (is (= (dissoc (cljs-eval "(+ 1 1)") :ms)
           {:tag :ret, :val "2", :ns "cljs.user",  :form "(+ 1 1)"}))
    (is (= (with-out-str (cljs-eval "(+ 1 1)"))
           ""))
    (is (= (dissoc (cljs-eval "(println 1 1)") :ms)
           {:tag :ret, :val "nil", :ns "cljs.user", :form "(println 1 1)"}))
    (is (= (with-out-str (cljs-eval "(println 1 1)"))
           "1 1\n"))
    (is (= (dissoc (cljs-eval "(foo 1 1)") :ms :val)
           {:tag :ret,
            :ns "cljs.user", :form "(foo 1 1)",
            :exception true}))
    (is (let [r (with-out-str (cljs-eval "(foo 1 1)"))]
          (.startsWith r "WARNING: Use of undeclared Var")))
    (is (= (dissoc (cljs-eval "(tap> 1 )") :ms)
           {:tag :ret, :val "true", :ns "cljs.user", :form "(tap> 1 )"})))
  (testing "cljs-eval!"
    (is (= (cljs-eval! (* 2 3))
           6))
    (is (= (cljs-eval! (println 2 3))
           nil))
    (is (= (cljs-eval! (tap> 2 3))
           true))
    (is (thrown? Exception (cljs-eval! (foo 2 3))))))
