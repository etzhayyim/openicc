(ns openicc.bot.defence
  "**弁護 bot** — Article 67, turned into a pipeline stage.

  Every other bot in openicc is trying to build a case. This one is trying to
  break it, and gate G6 will not let a dossier out until it has run and failed.

  The reason is structural rather than moral. A pipeline of agents that all
  share a goal produces findings that all share a bias, and the bias is
  invisible from inside because every stage agrees. The fix that works in this
  workspace's other adversarial passes is the same one here: an independent
  stage whose success condition is the *opposite* — it is rewarded for refuting,
  it defaults to `refuted` when it cannot tell, and its verdict is binding.

  Note the default. `refute` returns `:refuted? true` when the record is too
  thin to evaluate. A defence bot that abstains on thin evidence is not neutral;
  it is a rubber stamp with extra steps, because thin evidence is exactly the
  condition under which the other bots are most likely to be wrong."
  (:require [kotoba.lang.text :as str]
            [openicc.record :as rec]))

(def grounds
  "The refutation grounds this bot may return. Closed, so a ground can be
  counted, tested and argued with — a free-text objection cannot."
  [{:ground/id :single-source
    :ground/rule "A load-bearing assertion rests on one publisher."}
   {:ground/id :unpinned-evidence
    :ground/rule "No load-bearing source is content-pinned; the bytes cited today may not be the bytes retrieved tomorrow."}
   {:ground/id :contextual-element-unaddressed
    :ground/rule "A chapeau element was never addressed, so the underlying acts do not yet amount to an Article 5 crime."}
   {:ground/id :alternative-explanation
    :ground/rule "The same facts are consistent with a stated non-criminal or differently-criminal explanation."}
   {:ground/id :attribution-gap
    :ground/rule "Conduct is established but its attribution to the named person is not."}
   {:ground/id :temporal-gap
    :ground/rule "The conduct window is not established against the temporal floor."}
   {:ground/id :complementarity-unmeasured
    :ground/rule "Whether a State is genuinely acting was never observed, so Article 17 cannot be resolved."}
   {:ground/id :insufficient-record
    :ground/rule "The dossier is too thin to evaluate. This is the DEFAULT."}])

(def ground-ids (into #{} (map :ground/id) grounds))

(defn- load-bearing [dossier]
  (into [] (mapcat :finding/assertions) (vals (:dossier/findings dossier))))

(defn refute
  "Try to break the dossier. Returns

      {:ran? true :refuted? bool :grounds [...] :scanned n}

  Read directly by governor gate G6."
  [dossier {:keys [alternative-explanations attribution-established?] :as _ctx}]
  (let [as       (load-bearing dossier)
        findings (:dossier/findings dossier)
        named    (filterv rec/names-individual? as)
        gs (cond-> []
             (empty? as)
             (conj :insufficient-record)

             (some #(< (:assertion/independent-sources % 0) 2) named)
             (conj :single-source)

             (and (seq as) (not-any? #(some :source/pinned? (:assertion/sources %)) as))
             (conj :unpinned-evidence)

             (seq (get-in findings [:elements :finding/unaddressed]))
             (conj :contextual-element-unaddressed)

             (seq alternative-explanations)
             (conj :alternative-explanation)

             (and (seq named) (not attribution-established?))
             (conj :attribution-gap)

             (= :indeterminate (get-in findings [:jurisdiction :finding/verdict]))
             (conj :temporal-gap)

             (= :indeterminate (get-in findings [:admissibility :finding/verdict]))
             (conj :complementarity-unmeasured))]
    {:ran?     true
     :scanned  (count as)
     :refuted? (boolean (seq gs))
     :grounds  (vec (distinct gs))
     :note     (if (seq gs)
                 (str "refuted on " (str/join ", " (map name (distinct gs))))
                 "no ground of refutation found on this record")}))

(defn not-run
  "What the dossier carries before the defence bot has run. Kept as a named
  value so `:ran? false` is written once, here, rather than reconstructed by
  every caller — and so a caller cannot accidentally construct a passing
  rebuttal by omitting the key."
  []
  {:ran? false :refuted? false :grounds [] :scanned 0})
