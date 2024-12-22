# de.levering-it/cljs-eval

[![Clojars Project](https://img.shields.io/clojars/v/de.levering-it/cljs-eval.svg)](https://clojars.org/de.levering-it/cljs-eval)
![example branch parameter](https://github.com/HendrikLevering/cljs-eval/actions/workflows/clojure.yaml/badge.svg?branch=main)

Do you want to be able to run cljs directly from your clj repl?

Cljs-eval gives you an easy way to create a headless browser repl, which you can use to
run cljs code directly from clj.

## Getting Started

Include the dependency:

```clojure
de.levering-it/cljs-eval {:mvn/version "0.1.2"}
```


All functions are in the `de.levering-it/cljs-eval` namepace

Require needed dependencies:

```clojure
(require '[de.levering-it.cljs-eval :refer [enable-cljs-eval! cljs-eval cljs-eval! with-cljs read-fn]])
```

And you are ready to go:

```clojure
(cljs-eval! (+ 1 1)) ; => 2
```

cljs-eval! takes a form, evals it in the browser repl and returns the result.
It will also print stdout and stderr and forward tap.
The return value is parsed with edn/read-string. Therefore it will throw an exception, if the return value is not a valid edn. You can rebind read-fn to a function, which parses the return value in a different way.

```clojure
(binding [read-fn #(-> % :val identity)]
  (cljs-eval! (+ 1 1)))
; => "2"
```

You are realy evaluating in the context of the browser repl and you can use the result directly in CLJ

```clojure
(cljs-eval! (set! (.-title js/document) "Foobar"))
; => nil
(= "Foobar" (cljs-eval! (.-title js/document)))
; => true
```

## Using values from CLJ

cljs-eval! takes a form. It can be cumbersome to write this form manually especially if you want to use the result of a clojure expression in that form.
with-cljs! allows you to define symbols, whos value is the result of a clojure expression and use that symbol in the form. with-cljs works like a let, where every binding is evaluated in CLJ and the body is evaluated in the browser repl.

```clojure
(let [x 40
      y 2]
  (with-cljs [a x ; the right hand side of the bindings is evaluated in CLJ
              b y]
    (+ a b))) ; this is evaluated in the browser repl. a and b are replaced by the values from the CLJ evaluation.
; => 42
```

Destructuring works, too:

```clojure
(= (with-cljs [[{:keys [x]} b] [{:x 5} 2]
               x (* x 2)
               y x]
               ; this runs in the CLJS browser repl.  x y b are replaced by the values from the CLJ evaluation.
               (+ x y b)) 22)
; => true
```

A limitation is that evaluations in the bindings block must produce plain serializable values.

## Low level API
cljs-eval! and with-cljs! enable the cljs evaluation if it is not enabled already.
You can enable it manually:

```clojure
(enable-cljs-eval!)
```

> **Note:** The same evaluation context is used for all cljs-eval! calls as long as the JVM repl process is running.

There is also a low level function, which accepts a raw string.

```clojure
(cljs-eval "(println 1 1)") ; 1 1
; => {:tag :ret, :val "nil", :ns "cljs.user", :ms 10, :form "(println 1 1)"}
```

cljs-eval uses playwright under the hood. You can get the page object that contains the evaluation context:

```clojure
(get-page)
```

This can be useful if you need to trigger effects that cannot be triggered from the JS. For example a "click" event, that triggers default handlers.

## Limitations

Teardown of cljs repl from with the jvm process does not work. If you need to a fresh cljs repl, you have to restart your jvm repl. If you try to restart the cljs repl from within
the JVM process, then the next cljs-eval will freeze. This is due to some strange behaviour in the socket-repl or playwright thread, which I do not fully understand. Hints to fix this are
welcome :-).

cljs-eval! and with-cljs macro use edn/string to parse the result val by default. Any result which is not a plain datastructure will throw. You can
receive the origin result with ex-data.

> **Warning:** The cljs context under the hood is a socket repl. The repl is started in a headless browser. It uses http://localhost:9000 to host the repl and playwright to connect to that repl. Therefore take care that you do not use this library more than once in different JVM processes at the same time.

## License

Copyright © 2024 Levering IT GmbH

Distributed under MIT License
