# de.levering-it/cljs-eval

Do you want to be able to run cljs directly from your cljs repl?
Cljs-eval gives you a way to create a headless browser repl, which you can use to
run cljs code directly from clj.

## Getting Started

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
a macro, which evaluates forms and parses the return value:

    $ (cljs-eval! (+ 1 1))
    2

Note: That cljs-eval! throws, if it cannot parse the return value (edn/read-string ret-value)

## Usage
Run the project's tests (they'll fail until you edit them):

    $ clojure -T:build test

Run the project's CI pipeline and build a JAR (this will fail until you edit the tests to pass):

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ clojure -T:build install

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment
variables (requires the `ci` task be run first):

    $ clojure -T:build deploy

Your library will be deployed to de.levering-it/cljs-eval on clojars.org by default.

## License

Copyright © 2024 Levering IT GmnG

Distributed under MIT License
