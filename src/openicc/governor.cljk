(ns openicc.governor
  "The independent governor. The one invariant of this repository:

      openicc never publishes, sends or attests anything the governor refuses.

  The governor is a separate lineage from the bots on purpose. The bots reason;
  the governor only ever answers one question — *may this leave?* — and it
  answers it from the record, never from the bot's own account of itself.

  ## Why this is stricter than danjo's charter, not looser

  `cloud-itonami/danjo` is chartered NON-adjudicating: it observes and does not
  rate. openicc was chartered by the owner (2026-08-21) to do the opposite — to
  assess Article 17 admissibility and to draft Article 15(2) communications that
  name real States and real individuals. That is a heavier authority, so the
  gates here are heavier too, and openicc does **not** inherit danjo's charter;
  it carries its own.

  The structural answer to \"an autonomous bot that accuses people\" is not to
  make the bot careful. It is to make publication impossible for any single
  bot: gate G7 requires a 2f+1 quorum certificate over the finding's content
  address, so a named finding is published by a consensus or not at all. The
  bot is autonomous. It is not alone.

  ## The evidence floor

  Every gate below reports SCANNED counts, and a scan of zero is a refusal, not
  a pass. This is the failure this workspace has paid for repeatedly: a check
  that could not measure returns the same value as a check that measured and
  found nothing wrong. Here, `nothing to check` is `:refused` with reason
  `:no-evidence`, and the caller can tell the two apart."
  (:require [kotoba.lang.text :as str]
            [openicc.record :as rec]
            [openicc.statute :as statute]))

(def gates
  "The charter. Each gate is `{:gate/id :gate/rule}`; the predicate lives in
  `check-*` below and the two are joined by id in `admit`, so a gate that is
  declared but never evaluated is detectable (`declared-but-unevaluated`)."
  [{:gate/id :G1 :gate/rule
    "Closed crime set — a finding may only assert a crime named in Article 5(1).
     The Court's subject-matter jurisdiction is exhaustive; an open enum would
     let a bot invent a crime."}
   {:gate/id :G2 :gate/rule
    "Verified-registry-only citation — every article cited must resolve in
     openicc.statute AND be :verified. :unverified-seed is readable, not
     publishable."}
   {:gate/id :G3 :gate/rule
    "Non-fabrication — every assertion carries at least one source. Zero
     assertions is :no-evidence, never a pass."}
   {:gate/id :G4 :gate/rule
    "Named-individual corroboration — an assertion naming a natural person needs
     at least two INDEPENDENT publishers and at least one content-pinned source.
     Reprints of one wire story are one source."}
   {:gate/id :G5 :gate/rule
    "Complete pipeline — elements, jurisdiction, admissibility and interests
     findings must all be present. A missing limb is not a neutral limb."}
   {:gate/id :G6 :gate/rule
    "Adversarial rebuttal — the defence bot must have actually run against this
     finding set and must have failed to refute it. Not-run is a refusal."}
   {:gate/id :G7 :gate/rule
    "Quorum publication — a named finding requires a 2f+1 quorum certificate
     over its content address. No single bot may publish a name."}
   {:gate/id :G8 :gate/rule
    "Article 15(2) channel only — the sole outbound destination is the Office of
     the Prosecutor. Never the press, never the named party, never a third
     party, never a public list."}
   {:gate/id :G9 :gate/rule
    "Article 17 scope for rating — the only permitted assessment of a State is
     an Article 17 admissibility assessment expressed in the Statute's own
     limbs. No general-purpose ranking, index, league table or scoreboard."}
   {:gate/id :G10 :gate/rule
    "Temporal floor — conduct before 2002-07-01, or before entry into force for
     the State concerned, is outside jurisdiction ratione temporis."}
   {:gate/id :G11 :gate/rule
    "Lawful-collection only — public sources and lawful disclosures. No
     intrusion, no compromised material, no bot-detection evasion, no
     circumvention of access controls."}
   {:gate/id :G12 :gate/rule
    "Append-only lineage — every commit and every hold is recorded with its
     inputs' content addresses. A refusal is a record, not a silence."}
   {:gate/id :G13 :gate/rule
    "Presumption of innocence (Article 66) — output language states allegation
     and source, never guilt. A communication requests examination; it does not
     pronounce."}
   {:gate/id :G14 :gate/rule
    "Murakumo-only inference — model calls resolve through the murakumo-main
     alias. No third-party inference endpoint on this path."}])

(def gate-ids (into #{} (map :gate/id) gates))

;; ---------------------------------------------------------------------------
;; Individual gates. Each returns nil (pass) or a refusal map.
;; ---------------------------------------------------------------------------

(defn- refusal [id reason detail]
  {:gate/id id :refusal/reason reason :refusal/detail detail})

(defn- assertions-of [dossier]
  (into [] (mapcat :finding/assertions) (vals (:dossier/findings dossier))))

(defn- citations-of [dossier]
  (into [] (comp (mapcat (fn [f] (concat (:finding/cites f)
                                         (mapcat :assertion/cites (:finding/assertions f)))))
                 (distinct))
        (vals (:dossier/findings dossier))))

(defn check-G1 [dossier]
  (let [claimed (into #{} (keep :finding/crime) (vals (:dossier/findings dossier)))
        outside (into #{} (remove statute/crime-ids) claimed)]
    (cond
      (empty? claimed) (refusal :G1 :no-evidence
                                {:scanned 0 :note "no finding names an Article 5(1) crime"})
      (seq outside)    (refusal :G1 :crime-outside-article-5
                                {:scanned (count claimed) :outside outside}))))

(defn check-G2 [dossier]
  (let [cites   (citations-of dossier)
        unverif (statute/unverified-citations cites)]
    (cond
      (empty? cites) (refusal :G2 :no-evidence {:scanned 0})
      (seq unverif)  (refusal :G2 :unverified-citation
                              {:scanned (count cites) :unverified (vec unverif)}))))

(defn check-G3 [dossier]
  (let [as (assertions-of dossier)
        unsourced (remove (comp seq :assertion/sources) as)]
    (cond
      (empty? as)        (refusal :G3 :no-evidence {:scanned 0})
      (seq unsourced)    (refusal :G3 :unsourced-assertion
                                  {:scanned (count as)
                                   :unsourced (mapv :assertion/claim unsourced)}))))

(defn check-G4 [dossier]
  (let [named (filterv rec/names-individual? (assertions-of dossier))
        thin  (remove (fn [a]
                        (and (>= (:assertion/independent-sources a 0) 2)
                             (some :source/pinned? (:assertion/sources a))))
                      named)]
    ;; No named assertions is a PASS here, not :no-evidence — a dossier that
    ;; names nobody has nothing for this gate to guard. G3 already refused the
    ;; empty-dossier case.
    (when (seq thin)
      (refusal :G4 :insufficient-corroboration
               {:scanned (count named)
                :thin (mapv (fn [a] {:claim (:assertion/claim a)
                                     :independent-publishers (:assertion/independent-sources a)
                                     :pinned? (boolean (some :source/pinned? (:assertion/sources a)))})
                            thin)}))))

(def required-limbs #{:elements :jurisdiction :admissibility :interests})

(defn check-G5 [dossier]
  (let [present (set (keys (:dossier/findings dossier)))
        missing (into #{} (remove present) required-limbs)
        negative (into #{} (comp (filter (fn [[k _]] (required-limbs k)))
                                 (remove (fn [[_ f]] (rec/affirmative? f)))
                                 (map first))
                       (:dossier/findings dossier))]
    (cond
      (seq missing)  (refusal :G5 :incomplete-pipeline
                              {:scanned (count present) :missing missing})
      (seq negative) (refusal :G5 :limb-not-affirmative
                              {:scanned (count present) :not-affirmative negative}))))

(defn check-G6 [dossier]
  (let [{:keys [ran? refuted? grounds]} (:dossier/rebuttal dossier)]
    (cond
      (not ran?) (refusal :G6 :rebuttal-not-run
                          {:scanned 0
                           :note "the defence bot did not run; not-run is a refusal, not a pass"})
      refuted?   (refusal :G6 :refuted-by-defence
                          {:scanned 1 :grounds (vec (or grounds []))}))))

(defn check-G7 [dossier]
  (let [{:keys [cert quorum-met? signers]} (:dossier/quorum dossier)
        names? (some rec/names-individual? (assertions-of dossier))]
    (cond
      (not names?) nil                                ; nothing named, no quorum needed
      (nil? cert)  (refusal :G7 :no-quorum-certificate
                            {:scanned 0
                             :note "a named finding requires a 2f+1 certificate over its content address"})
      (not quorum-met?) (refusal :G7 :quorum-not-met
                                 {:scanned (count (or signers []))
                                  :signers (vec (or signers []))}))))

(def ^:const otp-channel :article-15-2-otp)

(defn check-G8 [dossier]
  (let [ch (get-in dossier [:dossier/dispatch :dispatch/channel])]
    (cond
      (nil? ch)              nil                      ; nothing being dispatched
      (not= otp-channel ch)  (refusal :G8 :channel-outside-article-15
                                      {:scanned 1 :channel ch
                                       :permitted otp-channel}))))

(defn check-G9 [dossier]
  (let [score (get-in dossier [:dossier/findings :admissibility])
        limbs (set (:finding/cites score))
        art17 (into #{} (filter #(str/starts-with? (str %) "17")) limbs)]
    (cond
      (nil? score) nil                                ; G5 already covers absence
      (:finding/ranking? score)
      (refusal :G9 :general-purpose-ranking
               {:scanned 1
                :note "an Article 17 assessment is about the Court's competence in one case, not a league table of States"})
      (empty? art17)
      (refusal :G9 :rating-outside-article-17
               {:scanned (count limbs) :cites (vec limbs)}))))

(defn check-G10 [dossier]
  (let [floor (:test/floor statute/temporal)
        conduct (:dossier/conduct-window dossier)
        {:keys [from state-entry-into-force]} conduct
        earliest (->> [floor state-entry-into-force] (remove nil?) (sort) (last))]
    (cond
      (nil? from) (refusal :G10 :no-evidence
                           {:scanned 0 :note "no conduct window recorded"})
      (and earliest (neg? (compare (str from) (str earliest))))
      (refusal :G10 :before-temporal-floor
               {:scanned 1 :conduct-from from :floor earliest}))))

(defn check-G11 [dossier]
  (let [srcs (into [] (mapcat :assertion/sources) (assertions-of dossier))
        bad  (filterv #(contains? #{:compromised :intrusion :access-control-bypass}
                                  (:source/kind %))
                      srcs)]
    (cond
      (empty? srcs) (refusal :G11 :no-evidence {:scanned 0})
      (seq bad)     (refusal :G11 :unlawful-collection
                             {:scanned (count srcs)
                              :kinds (into #{} (map :source/kind) bad)}))))

(defn check-G12 [dossier]
  (when-not (seq (:dossier/lineage dossier))
    (refusal :G12 :no-lineage
             {:scanned 0
              :note "every input must be recorded by content address"})))

(def ^:private guilt-language
  #{"is guilty" "guilty of" "committed the crime" "perpetrator of"
    "convicted" "we find that" "有罪" "犯人である"})

(defn check-G13 [dossier]
  (let [texts (into [] (comp (mapcat (fn [f] (cons (:finding/rationale f)
                                                   (map :assertion/claim (:finding/assertions f)))))
                             (map (comp str/lower str)))
                    (vals (:dossier/findings dossier)))
        hits  (filterv (fn [t] (some #(str/includes? t (str/lower %)) guilt-language)) texts)]
    (cond
      (empty? texts) (refusal :G13 :no-evidence {:scanned 0})
      (seq hits)     (refusal :G13 :pronounces-guilt
                              {:scanned (count texts) :hits hits}))))

(defn check-G14 [dossier]
  ;; Per finding, not over the aggregate set. Folding every finding's endpoint
  ;; into one set means a finding that recorded NOTHING disappears behind its
  ;; siblings that did — the aggregate is non-empty, the gate is satisfied, and
  ;; the unattributed finding sails through. Caught by
  ;; `g14-refuses-inference-off-the-murakumo-alias` on 2026-08-22, which is the
  ;; whole reason each gate is tested in both directions.
  (let [fs      (:dossier/findings dossier)
        silent  (into #{} (comp (remove (fn [[_ f]] (some? (:inference/endpoint f))))
                                (map first))
                      fs)
        off     (into {} (comp (filter (fn [[_ f]] (some? (:inference/endpoint f))))
                               (remove (fn [[_ f]] (= "murakumo-main" (:inference/endpoint f))))
                               (map (juxt first (comp :inference/endpoint second))))
                      fs)]
    (cond
      (empty? fs)  (refusal :G14 :no-evidence
                            {:scanned 0 :note "no findings to attribute"})
      (seq silent) (refusal :G14 :inference-not-attributed
                            {:scanned (count fs) :unattributed silent
                             :note "a finding that does not record which model produced it is refused, not assumed fine"})
      (seq off)    (refusal :G14 :non-murakumo-inference
                            {:scanned (count fs) :endpoints off}))))

(def checks
  {:G1 check-G1 :G2 check-G2 :G3 check-G3 :G4 check-G4 :G5 check-G5
   :G6 check-G6 :G7 check-G7 :G8 check-G8 :G9 check-G9 :G10 check-G10
   :G11 check-G11 :G12 check-G12 :G13 check-G13 :G14 check-G14})

(defn declared-but-unevaluated
  "Gates the charter declares that nothing evaluates. Non-empty means the
  charter is partly decorative, which is worse than a shorter charter."
  []
  (into #{} (remove (set (keys checks))) gate-ids))

;; ---------------------------------------------------------------------------
;; Admission
;; ---------------------------------------------------------------------------

(defn admit
  "The only entry point. Returns

      {:admitted? bool
       :refusals  [{:gate/id :refusal/reason :refusal/detail}...]
       :evaluated #{gate ids actually run}
       :at        <caller's clock value>}

  `:evaluated` is returned so a caller can tell `passed 14 gates` from
  `ran 3 gates and none complained`."
  ([dossier] (admit dossier nil))
  ([dossier at]
   (let [unevaluated (declared-but-unevaluated)
         results (into [] (keep (fn [[_ f]] (f dossier))) (sort-by key checks))
         refusals (cond-> results
                    (seq unevaluated)
                    (conj (refusal :charter :declared-but-unevaluated
                                   {:gates unevaluated})))]
     {:admitted? (empty? refusals)
      :refusals  refusals
      :evaluated (set (keys checks))
      :at        at})))

(defn explain
  "Human-readable refusal. Used in the ledger and in the operator log — a hold
  that says only `blocked` teaches nobody anything, and the next run repeats it."
  [{:keys [admitted? refusals evaluated]}]
  (if admitted?
    (str "ADMITTED — " (count evaluated) " gates evaluated, 0 refusals")
    (str "REFUSED — " (count refusals) " of " (count evaluated) " gates\n"
         (str/join "\n"
                   (for [r refusals]
                     (str "  " (name (:gate/id r)) " " (name (:refusal/reason r))
                          " " (pr-str (:refusal/detail r))))))))
