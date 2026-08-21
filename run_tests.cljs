#!/usr/bin/env nbb
;; The primary test path. nbb, not the JVM — CLAUDE.md's runtime order is
;; kotoba wasm > clojurewasm > ClojureScript > nbb > (JVM, bb), and everything
;; under src/ is portable .cljc with no dependencies, so there is nothing here
;; that needs a JVM.
;;
;;   nbb run_tests.cljs
;;
;; `clojure -M:test` runs the same .cljc suite on the JVM. Both are expected to
;; pass; a suite that only ever ran on one runtime is evidence about that
;; runtime.
(ns run-tests
  (:require ["fs" :as fs]
            [clojure.edn :as edn]
            [clojure.test :as t]
            [openicc.core-test]
            [openicc.governor :as gov]))

(defn check-manifest-matches-charter!
  "manifest.edn describes the gates; openicc.governor implements them. Two
  places saying the same thing is two places that can disagree, and the
  disagreement is silent — the manifest is what a reader opens first.

  Lives in the nbb runner rather than the portable suite because it reads a
  file, and `src/` has no filesystem."
  []
  (let [declared (into #{} (map (comp keyword :gate))
                       (:actor/gates (edn/read-string (fs/readFileSync "manifest.edn" "utf8"))))
        implemented gov/gate-ids]
    (when-not (= declared implemented)
      (println "manifest.edn and openicc.governor disagree about the charter:")
      (println "  declared only:    " (sort (remove implemented declared)))
      (println "  implemented only: " (sort (remove declared implemented)))
      (js/process.exit 1))
    (println (str "charter: " (count implemented) " gates, manifest and governor agree"))))

(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (println)
  (println (str "openicc — " (:test m) " tests, " (:pass m) " assertions passed, "
                (:fail m) " failed, " (:error m) " errored"))
  (when-not (t/successful? m)
    (js/process.exit 1))
  ;; A run that executed zero tests must not exit 0. This is the evidence floor
  ;; the governor applies to its own gates, applied to the suite that checks it.
  (when (zero? (:test m))
    (println "REFUSING to report a pass: the runner executed 0 tests")
    (js/process.exit 3)))

(check-manifest-matches-charter!)
(t/run-tests 'openicc.core-test)
