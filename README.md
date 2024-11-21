# de.levering-it/cljs-eval

Do you want to be able to run cljs directly from your clj repl?
Cljs-eval gives you a way to create a headless browser repl, which you can use to
run cljs code directly from clj. It uses playwright to establish the browser repl.

## Getting Started

Get dependency:

    {:deps {io.github.HendrikLevering/cljs-eval {:git/sha "f3d32a33af57b5ff3cb7f2a18a653b4d50f7ec63"}}}

All functions are in the `de.levering-it/cljs-eval` namepace

Require needed dependencies:

    $ (require '[de.levering-it.cljs-eval :refer [enable-cljs-eval! cljs-eval cljs-eval!]])
    nil

Then enable the cljs evaluation context:

    $ (enable-cljs-eval!)

Now you can eval in cljs:

    $ (cljs-eval "(println 1 1)")
    ; 1 1
    {:tag :ret, :val "nil", :ns "cljs.user", :ms 10, :form "(println 1 1)"}

cljs-eval takes a string and returns a map with the result. For convenience there is
a macro, which evaluates forms and parses the return value with edn/read-string:

    $ (cljs-eval! (+ 1 1))
    2

You are realy evaluating in the context of the browser repl and you can use the result directly in CLJ

    (cljs-eval! (set! (.-title js/document) "Foobar"))
    (= "Foobar" (cljs-eval! (.-title js/document))) ; => true

Note: That cljs-eval! throws, if it cannot parse the return value (edn/read-string ret-value)

## Limitations

Teardown of cljs repl from with the jvm process does not work. If you need to a fresh cljs repl, you have to restart your jvm repl. If you try to restart the cljs repl from within
the JVM process, then the next cljs-eval will freeze. This is due to some strange behaviour in the socket-repl or playwright thread, which I do not fully understand. Hints to fix this are
welcome :-).

If you use cljs-eval! macro, it uses edn/string to parse the result val. Any result which is not a plain datastructure will throw. You can
receive the origin result with ex-data

## License

Copyright © 2024 Levering IT GmbH

Distributed under MIT License
