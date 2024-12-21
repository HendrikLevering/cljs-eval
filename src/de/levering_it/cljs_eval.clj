(ns de.levering-it.cljs-eval
  (:require [cljs.core.server]
            [cljs.repl.node]
            [cljs.repl.browser]
            [clojure.core.server :as server]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]])
  (:import [com.microsoft.playwright Playwright BrowserType$LaunchOptions]))

(defonce reader (atom nil))
(defonce writer (atom nil))

(defonce session (atom nil))

(defn get-page
  "Returns the playwright page object, that is connected to the context in
   which the JS code is evaluated"
  []
  (when-let [[_ _ _ _ _ page] @session]
    page))

(def ^:dynamic read-fn edn/read-string)

(defn enable-cljs-eval!
  "starts the cljs repl with a headless browser"
  []
  (when-not @session
   (let [server (server/start-server {:accept 'cljs.core.server/io-prepl
                                      :address "127.0.0.1"
                                      :port 0
                                      :name "cljs.math-repl"
                                      :args [:repl-env (cljs.repl.browser/repl-env
                                                        :launch-browser false)]})
         port (-> server (.getLocalPort))]
     (println "Server opened on port" port)
     (let [p (Playwright/create)
           browser (.launch (.chromium p) (doto (BrowserType$LaunchOptions.)
                                            (.setHeadless true)))
           context (.newContext browser)
           socket  (java.net.Socket. "127.0.0.1"  (-> server (.getLocalPort)))
           rdr (io/reader socket)
           wrtr (io/writer socket)]
       (println "establish repl conn")
       (reset! reader rdr)
       (reset! writer wrtr)
       (println (binding [*out* @writer
                          *in* @reader]
                  (read-line))) ; read once, returns "Waiting for browser to connect to http://localhost:9000 ...\n"
       (let [page (.newPage context)]
         (println "connecting to " "http://localhost:9000")
         (.navigate page "http://localhost:9000")  ; use playwright to get a browser to connect to it
         (reset! session [server p browser socket context page]))))))

(defn disable-cljs-eval!
  "kill the cljs repl"
  ;this is buggy. If you run (enable-cljs-eval!) in the same jvm process again, then cljs-eval will hang.
  []
  (when-let [[server p browser socket context] @session]
    (.close context)
    (.close socket)
    (.close browser)
    (.close p)
    (.close server)
    (reset! session nil)))


(defn -readline []
  (-> (binding [*in* @reader]
        (read-line))
      edn/read-string))

(defn -println [expr]
  (binding [*out* @writer]
    (println expr)
    ))

(defn -drain [expr]
  (let [r (do
            (-println expr)
            (-readline))]
    (case (:tag r)
      :ret [r]
      (loop [result [r]]
        (let [v (-readline)
              result (conj result v)]
          (if (= :ret (:tag v))
            result
            (recur result)))))))

(defn cljs-eval
  "eval a string in the cljs repl. You can start the repl with enable-cljs-eval!"
  [expr]
  (if @session
    (last (for [r (-drain expr)]
            (let [val (:val r)]
              (case (:tag r)
                :ret r
                :out (print val)
                :err (print val)
                :tap (tap> val)))))
    (throw (ex-info "cljs repl not ready. did you forget to run (enable-cljs-eval!)?" {:expr expr}))))

(defmacro cljs-eval!
  "evals form in cljs repl (cljs-eval! (+ 1 1))"
  [form]
  `(let [r# (cljs-eval (with-out-str (pprint (quote ~form))))]
     (try
       (read-fn (:val r#))
       (catch Exception e#
         (throw (ex-info "eval returned non-plain data" r#))))))


(defn drain
  "consumes all output"
  []
  (loop []
    (when (-> @reader
              .ready)
      (do
        (println (-readline))
        (recur)))))


(defn replace-symbols [form sym-map]
  (cond
    (symbol? form) (get sym-map form form)
    (seq? form) (map #(replace-symbols % sym-map) form)
    (vector? form) (mapv #(replace-symbols % sym-map) form)
    (map? form) (reduce-kv (fn [m k v]
                             (assoc m
                                    (replace-symbols k sym-map)
                                    (replace-symbols v sym-map)))
                           {}
                           form)
    (set? form) (set (map #(replace-symbols % sym-map) form))
    :else form))


(defmacro with-cljs
  "eval form in cljs repl. bindings bind symbols to vals from clj.
   Destructuring works.
   Limitations: symbols must be bound to plain data, that can be read by a cljs repl
"
  [bindings & forms]

  (let [bindings (clojure.core/destructure bindings)
        opts (into {} (for [[k v] (partition 2 2 bindings)]
                        [(list 'quote k) k]))
        form `(do ~@forms)]
    `(let ~(vec bindings)
       (let [r# (cljs-eval (with-out-str (clojure.pprint/pprint  (replace-symbols (quote ~form) ~opts))))]
         (-> r#
             (try
               (read-fn (:val r#))
               (catch Exception e#
                 (throw (ex-info "eval returned non-plain data" r#)))))))))


(comment

  (enable-cljs-eval!)
  (disable-cljs-eval!)
  (drain)

  (cljs-eval! (dotimes [n 20]
                (println  2 n)))
  ;; 2 3
  ;nil

  (cljs-eval! (* 2 3))
  ;6

  (cljs-eval! (foo 2 3))
  ;; WARNING: Use of undeclared Var cljs.user/foo at line 64 <cljs repl>
  ; throws
  ; Execution error (ExceptionInfo) at de.levering-it.cljs-eval/eval11104 (REPL:113).
  ; eval returned non-plain data
  (cljs-eval! (tap> 2))
  ;true

  (cljs-eval "(+ 1 1)")
  ;;{:tag :ret, :val "2", :ns "cljs.user", :ms 12, :form "(+ 1 1)"}

  (cljs-eval "(println 1 1)")
  ;; 1 1
  ;{:tag :ret, :val "nil", :ns "cljs.user", :ms 10, :form "(println 1 1)"}

  (cljs-eval "(foo 1 1)")
  ;; WARNING: Use of undeclared Var cljs.user/foo at line 58 <cljs repl>
  ;{:tag :ret, :val "{:via [{:type clojure.lang.ExceptionInfo, :message \"Execution error (TypeError) at (<cljs repl>:1).\\nCannot read properties of undefined (reading 'call')\\n\", :data {:type :js-eval-exception, :error {:status :exception, :value \"Execution error (TypeError) at (<cljs repl>:1).\\nCannot read properties of undefined (reading 'call')\\n\"}, :repl-env #cljs.repl.browser.BrowserEnv{:es #object[java.util.concurrent.ThreadPoolExecutor 0x1f18edcb \"java.util.concurrent.ThreadPoolExecutor@1f18edcb[Running, pool size = 16, active threads = 0, queued tasks = 0, completed tasks = 133]\"], :browser-state #atom[{:return-value-fn #function[cljs.repl.browser/browser-eval/fn--9268], :client-js #object[java.net.URL 0x556b227f \"jar:file:/Users/hendrik/.m2/repository/org/clojure/clojurescript/1.11.132/clojurescript-1.11.132.jar!/brepl_client.js\"], :closure-defines {}} 0x59b33b1b], :working-dir \".repl-1.11.132\", :preloaded-libs [], :launch-browser false, :static-dir [\".\" \"out/\"], :src \"src/\", :port 9000, :ordering #agent[{:status :ready, :val {:expecting 68, :fns {}}} 0x6281b899], :host \"localhost\", :server-state #atom[{:socket #object[java.net.ServerSocket 0x1d396f0f \"ServerSocket[addr=0.0.0.0/0.0.0.0,localport=9000]\"], :listeners 1, :port 9000} 0x47a7eb21]}, :form (foo 1 1), :js \"try{cljs.core.pr_str.call(null,(function (){var ret__7841__auto__ = cljs.user.foo.call(null,(1),(1));\\n(cljs.core._STAR_3 = cljs.core._STAR_2);\\n\\n(cljs.core._STAR_2 = cljs.core._STAR_1);\\n\\n(cljs.core._STAR_1 = ret__7841__auto__);\\n\\nreturn ret__7841__auto__;\\n})());\\n}catch (e11082){var e__7842__auto___11083 = e11082;\\n(cljs.core._STAR_e = e__7842__auto___11083);\\n\\nthrow e__7842__auto___11083;\\n}\"}, :at [cljs.repl$evaluate_form invokeStatic \"repl.cljc\" 577]}], :trace [[cljs.repl$evaluate_form invokeStatic \"repl.cljc\" 577] [cljs.repl$evaluate_form invoke \"repl.cljc\" 498] [cljs.repl$eval_cljs invokeStatic \"repl.cljc\" 692] [cljs.repl$eval_cljs invoke \"repl.cljc\" 685] [cljs.core.server$prepl$fn__8211$fn__8216 invoke \"server.clj\" 106] [cljs.core.server$prepl$fn__8211 invoke \"server.clj\" 91] [cljs.compiler$with_core_cljs invokeStatic \"compiler.cljc\" 1478] [cljs.compiler$with_core_cljs invoke \"compiler.cljc\" 1467] [cljs.core.server$prepl invokeStatic \"server.clj\" 77] [cljs.core.server$prepl doInvoke \"server.clj\" 39] [clojure.lang.RestFn invoke \"RestFn.java\" 473] [cljs.core.server$io_prepl invokeStatic \"server.clj\" 140] [cljs.core.server$io_prepl doInvoke \"server.clj\" 132] [clojure.lang.RestFn applyTo \"RestFn.java\" 140] [clojure.lang.Var applyTo \"Var.java\" 707] [clojure.core$apply invokeStatic \"core.clj\" 667] [clojure.core.server$accept_connection invokeStatic \"server.clj\" 74] [clojure.core.server$start_server$fn__9040$fn__9041$fn__9043 invoke \"server.clj\" 118] [clojure.lang.AFn run \"AFn.java\" 22] [java.lang.Thread run \"Thread.java\" 1583]], :cause \"Execution error (TypeError) at (<cljs repl>:1).\\nCannot read properties of undefined (reading 'call')\\n\", :data {:type :js-eval-exception, :error {:status :exception, :value \"Execution error (TypeError) at (<cljs repl>:1).\\nCannot read properties of undefined (reading 'call')\\n\"}, :repl-env #cljs.repl.browser.BrowserEnv{:es #object[java.util.concurrent.ThreadPoolExecutor 0x1f18edcb \"java.util.concurrent.ThreadPoolExecutor@1f18edcb[Running, pool size = 16, active threads = 0, queued tasks = 0, completed tasks = 133]\"], :browser-state #atom[{:return-value-fn #function[cljs.repl.browser/browser-eval/fn--9268], :client-js #object[java.net.URL 0x556b227f \"jar:file:/Users/hendrik/.m2/repository/org/clojure/clojurescript/1.11.132/clojurescript-1.11.132.jar!/brepl_client.js\"], :closure-defines {}} 0x59b33b1b], :working-dir \".repl-1.11.132\", :preloaded-libs [], :launch-browser false, :static-dir [\".\" \"out/\"], :src \"src/\", :port 9000, :ordering #agent[{:status :ready, :val {:expecting 68, :fns {}}} 0x6281b899], :host \"localhost\", :server-state #atom[{:socket #object[java.net.ServerSocket 0x1d396f0f \"ServerSocket[addr=0.0.0.0/0.0.0.0,localport=9000]\"], :listeners 1, :port 9000} 0x47a7eb21]}, :form (foo 1 1), :js \"try{cljs.core.pr_str.call(null,(function (){var ret__7841__auto__ = cljs.user.foo.call(null,(1),(1));\\n(cljs.core._STAR_3 = cljs.core._STAR_2);\\n\\n(cljs.core._STAR_2 = cljs.core._STAR_1);\\n\\n(cljs.core._STAR_1 = ret__7841__auto__);\\n\\nreturn ret__7841__auto__;\\n})());\\n}catch (e11082){var e__7842__auto___11083 = e11082;\\n(cljs.core._STAR_e = e__7842__auto___11083);\\n\\nthrow e__7842__auto___11083;\\n}\"}}", :ns "cljs.user", :form "(foo 1 1)", :exception true}

  (cljs-eval "(tap> 1 )")
  ;{:tag :ret, :val "true", :ns "cljs.user", :ms 10, :form "(tap> 1 )"}




  ; drain helper for unexpecteed cases
  (when (-> @reader
            .ready)
    (-readline))
  )
