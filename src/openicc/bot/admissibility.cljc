(ns openicc.bot.admissibility
  "**受理可能性 bot** — Article 17. Complementarity and gravity.

  This is the bot the owner's 2026-08-21 scoping decision actually turns on:
  openicc *does* rate, where danjo does not. So it matters exactly what is
  being rated.

  **Article 17 is a question about the Court, not a verdict about a State.**
  The Statute is drafted as a list of reasons the Court must stand down. A case
  is inadmissible when a State with jurisdiction is genuinely acting. So the
  affirmative output here — `admissible` — is a statement that *the Court may
  proceed*, which is not the same sentence as `this State is bad`, and gate G9
  refuses any output that reshapes it into one.

  Two consequences follow that a naive scorer gets backwards:

  * **`No national proceedings` is not a finding against a State.** It is the
    absence of an inadmissibility ground. A State with no proceedings because
    the conduct never occurred scores identically to one that is stonewalling,
    and this function must not pretend otherwise — it returns `:indeterminate`,
    not `:affirmative`, when it cannot see whether proceedings exist.
  * **`Unwilling` is a closed list.** Article 17(2) enumerates shielding,
    unjustified delay, and lack of independence or impartiality. openicc may
    cite those three and nothing else. An impression of bad faith that fits
    none of them is not a finding."
  (:require [openicc.record :as rec]
            [openicc.statute :as statute]))

(def ^:private unwilling-ids
  (into #{} (map :indicium/id) statute/unwilling-indicia))

(def ^:private unable-ids
  (into #{} (map :indicium/id) statute/unable-indicia))

(defn unwilling-or-unable
  "Article 17(2)/(3). Returns the cited indicia, or an empty vector.

  Indicia outside the closed list are dropped and reported separately: a caller
  that passed `:seems-political` needs to see that it was ignored, not to have
  it quietly folded into the count."
  [indicia]
  (let [ind (set indicia)]
    {:cited   (vec (sort (filter (into unwilling-ids unable-ids) ind)))
     :ignored (vec (sort (remove (into unwilling-ids unable-ids) ind)))}))

(defn gravity-sufficient?
  "Article 17(1)(d), assessed against the OTP's four stated policy factors.

  `:indeterminate` when fewer than two factors were measured at all. Scoring
  `insufficient` from unmeasured factors is the same defect as reporting a
  clean scan you never ran."
  [factors]
  (let [measured (into {} (filter (comp some? val)) (select-keys factors [:scale :nature :manner :impact]))]
    (cond
      (< (count measured) 2) :indeterminate
      (some #(= :high %) (vals measured)) true
      (every? #(= :low %) (vals measured)) false
      :else :indeterminate)))

(defn assess
  "Returns a finding whose verdict is:

    :affirmative   — no inadmissibility ground stands; the Court may proceed
    :negative      — an Article 17(1) limb makes the case inadmissible
    :indeterminate — the record does not show whether a limb applies

  `:indeterminate` is the honest default and the most common correct answer for
  an open-source dossier, because national proceedings are frequently not
  publicly observable."
  [{:keys [situation-id national-proceedings? national-decision-not-to-prosecute?
           already-tried? unwillingness-indicia gravity-factors]}
   {:keys [at]}]
  (let [{:keys [cited ignored]} (unwilling-or-unable unwillingness-indicia)
        escape? (seq cited)
        gravity (gravity-sufficient? gravity-factors)
        cites   (cond-> ["17(1)(d)"]
                  (some? national-proceedings?)               (conj "17(1)(a)")
                  (some? national-decision-not-to-prosecute?) (conj "17(1)(b)")
                  (some? already-tried?)                      (conj "17(1)(c)")
                  escape? (into (map (fn [i]
                                       (:indicium/article
                                        (first (filter #(= i (:indicium/id %))
                                                       (concat statute/unwilling-indicia
                                                               statute/unable-indicia)))))
                                     cited)))
        verdict (cond
                  already-tried?                      :negative   ; 17(1)(c)
                  (= false gravity)                   :negative   ; 17(1)(d)
                  (= :indeterminate gravity)          :indeterminate
                  ;; 17(1)(a)/(b): a State genuinely acting closes the door,
                  ;; unless an enumerated indicium reopens it.
                  (and (or national-proceedings?
                           national-decision-not-to-prosecute?)
                       (not escape?))                 :negative
                  ;; We could not see whether a State is acting. Not a finding.
                  (and (nil? national-proceedings?)
                       (nil? national-decision-not-to-prosecute?)) :indeterminate
                  :else                               :affirmative)]
    (-> (rec/finding
         {:bot          :admissibility
          :kind         :admissibility
          :verdict      verdict
          :situation-id situation-id
          :at           at
          :cites        (vec (distinct (remove nil? cites)))
          :rationale
          (str "Article 17 assessment of the Court's competence in this case. "
               "National proceedings: " (pr-str national-proceedings?) ". "
               "Prior trial: " (pr-str already-tried?) ". "
               "Gravity (OTP policy factors, not statutory text): " (pr-str gravity) ". "
               "Article 17(2)/(3) indicia cited: " (pr-str cited) "."
               (when (seq ignored)
                 (str " Indicia OUTSIDE the closed Article 17(2)/(3) list, ignored: "
                      (pr-str ignored) ".")))})
        ;; Read by gate G9. This assessment is about one case before the Court.
        ;; It is not, and may not be rendered as, a ranking of States.
        (assoc :finding/ranking? false
               :finding/ignored-indicia (vec ignored)
               :finding/gravity gravity))))
