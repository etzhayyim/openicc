(ns openicc.bot.jurisdiction
  "**管轄 bot** — Articles 11, 12, 13. Chambers' first question: may the Court
  reach this conduct at all?

  This is a decision core, not an advisory one. Every input is a fact somebody
  else established (which State, which dates, which trigger); the answer follows
  from the Statute. No model is consulted, so no model can be wrong about it.

  Three traps this function exists to avoid, each of which produces a confident
  wrong answer rather than an error:

  1. **A Security Council referral does not need Article 12(2).** Applying the
     territorial/nationality preconditions to a 13(b) situation reports `no
     jurisdiction` over exactly the situations the Statute designed 13(b) to
     reach.
  2. **The temporal floor is per-State, not global.** 2002-07-01 is the floor
     for the Statute; a State that acceded in 2015 carries a 2015 floor, and
     using the global one silently extends jurisdiction backwards by 13 years.
  3. **Aggression is not symmetric with the other three crimes.** The Kampala
     regime excludes nationals and territory of States that have not ratified
     the amendments, and 15bis/15ter route differently. Treating aggression
     like Article 7 reaches people the Court cannot reach."
  (:require [openicc.record :as rec]
            [openicc.statute :as statute]))

(defn- later [a b]
  (cond (nil? a) b (nil? b) a
        (neg? (compare (str a) (str b))) b :else a))

(defn temporal-floor
  "The earliest date conduct may bear for this situation: the Statute's own
  entry into force, or the State's, whichever is later. An Article 12(3)
  declaration may reach back before accession, so it is honoured when present."
  [{:keys [state-entry-into-force art-12-3-declaration-from]}]
  (or art-12-3-declaration-from
      (later (:test/floor statute/temporal) state-entry-into-force)))

(defn within-temporal?
  [{:keys [conduct-from] :as situation}]
  (let [floor (temporal-floor situation)]
    (cond
      (nil? conduct-from) :indeterminate
      (neg? (compare (str conduct-from) (str floor))) false
      :else true)))

(defn preconditions-satisfied
  "Article 12(2)/(3). Returns the id of the limb that carries it, or nil.
  Named limbs rather than a boolean because a finding must cite *which*
  precondition it relied on."
  [{:keys [territory-state-party? accused-nationality-state-party?
           art-12-3-declaration?]}]
  (cond
    territory-state-party?            :territorial
    accused-nationality-state-party?  :nationality
    art-12-3-declaration?             :ad-hoc-declaration))

(defn aggression-reachable?
  "Article 15bis(5). For the crime of aggression under a State referral or
  proprio motu, the Court has no jurisdiction over nationals or territory of a
  State that has not ratified the Kampala amendments. A Security Council
  referral (15ter) is not so limited."
  [{:keys [trigger kampala-ratified-by-territory-state?
           kampala-ratified-by-nationality-state?]}]
  (if (= :security-council-referral trigger)
    true
    (boolean (or kampala-ratified-by-territory-state?
                 kampala-ratified-by-nationality-state?))))

(defn assess
  "The whole Article 11-13 question. Returns a finding; publishes nothing."
  [{:keys [situation-id crime trigger] :as situation} {:keys [at]}]
  (let [trig      (statute/trigger trigger)
        temporal  (within-temporal? situation)
        needs-12? (get trig :trigger/requires-art-12-2? true)
        limb      (preconditions-satisfied situation)
        aggr-ok?  (or (not= :aggression crime) (aggression-reachable? situation))
        cites     (cond-> ["11" (:trigger/article trig)]
                    limb      (conj (:precondition/article
                                     (first (filter #(= limb (:precondition/id %))
                                                    statute/preconditions))))
                    (= :aggression crime) (conj "8bis"))
        verdict   (cond
                    (nil? trig)               :negative
                    (= :indeterminate temporal) :indeterminate
                    (false? temporal)         :negative
                    (and needs-12? (nil? limb)) :negative
                    (not aggr-ok?)            :negative
                    :else                     :affirmative)]
    (rec/finding
     {:bot          :jurisdiction
      :kind         :jurisdiction
      :verdict      verdict
      :situation-id situation-id
      :at           at
      :cites        (vec (remove nil? cites))
      :rationale
      (str "Trigger " (pr-str trigger)
           (if needs-12?
             (str "; Article 12(2) required and "
                  (if limb (str "satisfied via " (name limb)) "NOT satisfied"))
             "; Article 12(2) not required for a Security Council referral")
           "; temporal floor " (temporal-floor situation)
           ", conduct from " (pr-str (:conduct-from situation))
           (when (= :aggression crime)
             (str "; Kampala reachability " (pr-str aggr-ok?))))})))

(defn finding->with-crime
  "Carry the crime id onto the finding so gate G1 can read it. Kept separate
  from `assess` so the crime is attached by the caller that chose it, not
  invented by the assessor."
  [finding crime]
  (assoc finding :finding/crime crime))
