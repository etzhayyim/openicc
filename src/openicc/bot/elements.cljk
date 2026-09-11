(ns openicc.bot.elements
  "**構成要件 bot** — Articles 6, 7, 8, 8bis and the Elements of Crimes.

  Does the alleged conduct fit the chapeau of a crime in Article 5(1)?

  The chapeau is where cases are actually won and lost, and it is where a
  language model is most likely to be fluent and wrong: the underlying acts
  (killing, torture, deportation) are easy to recognise and are *not* what makes
  something an ICC crime. What makes it one is the contextual element —

    genocide           intent to destroy a protected group, as such (Art. 6)
    crimes v humanity  widespread OR systematic attack on a civilian
                       population, with knowledge of it (Art. 7)
    war crimes         a nexus to armed conflict, and its character (Art. 8)
    aggression         a leadership position, and manifest Charter violation (8bis)

  So this bot refuses to return `:affirmative` on underlying acts alone. Every
  contextual element must be separately asserted and separately sourced, and a
  contextual element that was never addressed is `:indeterminate` — not absent,
  not present, *unaddressed*, which is a distinct and reportable state."
  (:require [openicc.record :as rec]
            [openicc.statute :as statute]))

(def contextual-elements
  "What each crime requires beyond the underlying act, keyed by crime id."
  {:genocide
   [{:element/id :protected-group     :element/article "6"
     :element/rule "The victims belonged to a national, ethnical, racial or religious group."}
    {:element/id :dolus-specialis     :element/article "6"
     :element/rule "Intent to destroy that group in whole or in part, as such."}]
   :crimes-against-humanity
   [{:element/id :attack-on-civilians :element/article "7(1)"
     :element/rule "An attack directed against any civilian population."}
    {:element/id :widespread-or-systematic :element/article "7(1)"
     :element/rule "The attack was widespread OR systematic (disjunctive, not both)."}
    {:element/id :state-or-org-policy :element/article "7(2)(a)"
     :element/rule "Pursuant to or in furtherance of a State or organizational policy."}
    {:element/id :knowledge-of-attack :element/article "7(1)"
     :element/rule "The perpetrator knew the conduct was part of the attack."}]
   :war-crimes
   [{:element/id :armed-conflict      :element/article "8(2)"
     :element/rule "An armed conflict existed."}
    {:element/id :conflict-character  :element/article "8(2)"
     :element/rule "Its character (international or non-international) is established, because it selects which sub-paragraphs apply at all."}
    {:element/id :nexus               :element/article "8(2)"
     :element/rule "The conduct took place in the context of and was associated with that conflict."}
    {:element/id :awareness           :element/article "8(2)"
     :element/rule "The perpetrator was aware of the factual circumstances establishing the conflict."}]
   :aggression
   [{:element/id :leadership          :element/article "8bis(1)"
     :element/rule "The person was in a position effectively to exercise control over or to direct the political or military action of a State."}
    {:element/id :act-of-aggression   :element/article "8bis(2)"
     :element/rule "An act of aggression as defined, by reference to UNGA Resolution 3314 (XXIX)."}
    {:element/id :manifest-violation  :element/article "8bis(1)"
     :element/rule "By its character, gravity and scale, a manifest violation of the Charter of the United Nations."}]})

(defn- state-of
  "Three-valued read of one element from the caller's addressed map."
  [addressed id]
  (if (contains? addressed id)
    (if (get addressed id) :established :not-established)
    :unaddressed))

(defn assess
  "`addressed` maps element ids to true/false. Ids absent from the map are
  `:unaddressed`, which is why the map is read with `contains?` rather than
  `get` — `{:nexus false}` and `{}` are different claims and must not collapse."
  [{:keys [situation-id crime addressed underlying-acts]} {:keys [at]}]
  (when-not (statute/crime-ids crime)
    (throw (ex-info "crime outside Article 5(1)"
                    {:openicc/refusal :crime-outside-article-5 :crime crime})))
  (let [required (get contextual-elements crime)
        states   (into {} (map (juxt :element/id #(state-of addressed (:element/id %)))) required)
        missing  (into [] (comp (filter #(= :unaddressed (val %))) (map key)) states)
        failed   (into [] (comp (filter #(= :not-established (val %))) (map key)) states)
        verdict  (cond
                   (empty? underlying-acts) :indeterminate
                   (seq failed)             :negative
                   (seq missing)            :indeterminate
                   :else                    :affirmative)]
    (-> (rec/finding
         {:bot          :elements
          :kind         :elements
          :verdict      verdict
          :situation-id situation-id
          :at           at
          :cites        (into [(:crime/definition-article (statute/crime crime))]
                              (distinct (map :element/article required)))
          :rationale
          (str "Crime " (pr-str crime) ". Underlying acts alleged: "
               (count (or underlying-acts [])) ". Contextual elements: "
               (pr-str states) "."
               (when (seq missing)
                 (str " UNADDRESSED (neither established nor excluded): "
                      (pr-str missing) " — reported as indeterminate rather than"
                      " folded into either side."))
               (when (seq failed)
                 (str " NOT established: " (pr-str failed) ".")))})
        (assoc :finding/crime crime
               :finding/element-states states
               :finding/unaddressed (vec missing)))))
