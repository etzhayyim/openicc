(ns openicc.bot.examiner
  "**予備審査 bot** — Article 15(2). The intake stage.

  The Prosecutor `shall analyse the seriousness of the information received`.
  This bot does the reading: it turns open sources into addressed assertions,
  and it is the only stage that touches the outside world, so it is the only
  stage where lawfulness of collection is decidable (gate G11).

  Its refusals are deliberately loud. An examiner that quietly drops a source
  it could not fetch produces a dossier that looks thinner than the record and
  gives no way to tell `we looked and found little` from `we could not look`.
  So `intake` returns both the accepted items and the rejected ones, with why."
  (:require [kotoba.lang.text :as str]
            [openicc.record :as rec]))

(def prohibited-source-kinds
  "Article 69(7) excludes evidence obtained by means of a violation of the
  Statute or internationally recognized human rights where it casts substantial
  doubt on reliability or would seriously damage the integrity of the
  proceedings. openicc draws the line earlier and refuses at intake, because a
  source admitted here has already been copied, addressed and replicated by the
  time a chamber could exclude it."
  #{:compromised :intrusion :access-control-bypass :captcha-bypass :paywall-bypass})

(defn- reject [item reason]
  {:rejected/item item :rejected/reason reason})

(defn intake
  "Turn raw items into sourced assertions.

  Each item: `{:claim :sources :cites :subject-kind}`. Returns
  `{:accepted [...] :rejected [...] :scanned n}` — the scan count is reported so
  a caller can tell an empty dossier from an empty inbox."
  [items]
  (reduce
   (fn [acc item]
     (let [{:keys [claim sources]} item
           bad (filterv (comp prohibited-source-kinds :source/kind) sources)]
       (cond
         (str/blank? (str claim))
         (update acc :rejected conj (reject item :no-claim))

         (empty? sources)
         (update acc :rejected conj (reject item :no-source))

         (seq bad)
         (update acc :rejected conj
                 (reject item {:unlawful-collection (into #{} (map :source/kind) bad)}))

         :else
         (update acc :accepted conj (rec/assertion item)))))
   {:accepted [] :rejected [] :scanned (count items)}
   items))

(defn seriousness
  "Article 15(2)'s own word. A coarse, stated-basis triage over the accepted
  assertions — never a substitute for the Article 17 gravity assessment, which
  lives in `openicc.bot.admissibility` and is the one that may be cited.

  Returns `:indeterminate` on an empty accepted set rather than `:low`. An
  inbox we could not read is not a situation that is not serious."
  [{:keys [accepted]}]
  (let [n         (count accepted)
        corrob    (count (filterv #(>= (:assertion/independent-sources % 0) 2) accepted))
        pinned    (count (filterv #(some :source/pinned? (:assertion/sources %)) accepted))]
    {:scanned n
     :corroborated corrob
     :pinned pinned
     :triage (cond
               (zero? n)                       :indeterminate
               (and (>= corrob 3) (pos? pinned)) :warrants-analysis
               (pos? corrob)                   :warrants-collection
               :else                           :insufficient)
     :note "Article 15(2) triage only. NOT the Article 17(1)(d) gravity test."}))
