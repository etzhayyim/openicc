(ns openicc.core
  "openicc — the pipeline, wired.

  Nine bots mirroring the Court's own organs, one governor that can stop any of
  them, and a consensus plane that decides whether a named finding may exist.

      examiner      Art. 15(2)  read open sources into addressed assertions
      elements      Art. 6-8bis do the facts meet a chapeau?
      jurisdiction  Art. 11-13  may the Court reach this conduct?
      admissibility Art. 17     is the Court competent, given the State?
      interests     Art. 53(1)(c) is there a reason to stand down?
      defence       Art. 67     try to break all of the above
      communication Art. 15(2)  draft the submission to the Prosecutor
      registry      Art. 43     custody: blocks, ledger, anchor
      assembly      Art. 112    the quorum that alone may publish a name

  `build-dossier` runs the analytic bots and returns a dossier. It publishes
  nothing and sends nothing: `openicc.governor/admit` decides, and
  `openicc.tick/run-tick` is what actually moves anything outward, and only
  after a quorum certificate exists.

  Everything is injected — clock, hash, model endpoint. There is no ambient
  authority in this namespace and no default for any of it."
  (:require [openicc.bot.admissibility :as admissibility]
            [openicc.bot.communication :as communication]
            [openicc.bot.defence :as defence]
            [openicc.bot.elements :as elements]
            [openicc.bot.examiner :as examiner]
            [openicc.bot.interests :as interests]
            [openicc.bot.jurisdiction :as jurisdiction]
            [openicc.governor :as gov]
            [openicc.record :as rec]))

(defn- stamp
  "Record which model produced a finding. Gate G14 reads this, and a finding
  that does not say is refused rather than assumed to be fine."
  [finding endpoint]
  (assoc finding :inference/endpoint endpoint))

(defn build-dossier
  "Run the analytic bots over one situation.

  `situation` carries the facts the examiner established plus the jurisdictional
  and complementarity inputs. `ctx` carries `:at`, `:hash-fn` and
  `:inference-endpoint` (which must resolve through the murakumo-main alias —
  see CLAUDE.md; a concrete model id here would be stale within weeks).

  Returns a dossier ready for `defence/refute` and then the governor. The
  rebuttal slot is filled with `defence/not-run`, which is a REFUSAL state, so a
  caller that forgets to run the defence bot gets a refusal rather than a pass."
  [{:keys [situation-id crime raw-items] :as situation}
   {:keys [at inference-endpoint] :as ctx}]
  (let [intake  (examiner/intake (or raw-items []))
        as      (:accepted intake)
        el      (-> (elements/assess (assoc situation :underlying-acts as) ctx)
                    (assoc :finding/assertions as)
                    (stamp inference-endpoint))
        ju      (-> (jurisdiction/assess situation ctx)
                    (jurisdiction/finding->with-crime crime)
                    (stamp inference-endpoint))
        ad      (-> (admissibility/assess situation ctx)
                    (assoc :finding/crime crime)
                    (stamp inference-endpoint))
        io      (-> (interests/assess situation ctx)
                    (assoc :finding/crime crime)
                    (stamp inference-endpoint))]
    {:dossier/situation-id situation-id
     :dossier/crime        crime
     :dossier/findings     {:elements el :jurisdiction ju
                            :admissibility ad :interests io}
     :dossier/intake       intake
     :dossier/triage       (examiner/seriousness intake)
     :dossier/rebuttal     (defence/not-run)
     :dossier/conduct-window {:from (:conduct-from situation)
                              :state-entry-into-force (:state-entry-into-force situation)}
     :dossier/lineage      (into [] (comp (mapcat :assertion/sources)
                                          (keep :source/cid)
                                          (distinct))
                                 as)
     :dossier/at           at}))

(defn run-defence
  "Fill the rebuttal slot for real."
  [dossier ctx]
  (assoc dossier :dossier/rebuttal (defence/refute dossier ctx)))

(defn draft
  "Compose the Article 15(2) communication and attach its dispatch envelope.

  Refuses if the governor would refuse. Composing a submission that cannot be
  sent is not harmless: the draft is a document naming real people, and its
  existence on disk is itself a disclosure risk."
  [dossier {:keys [at hash-fn submitter-did language] :as _ctx}]
  (let [adm (gov/admit dossier at)]
    (if-not (:admitted? adm)
      {:drafted? false :refusal (gov/explain adm) :admission adm}
      (let [as   (into [] (mapcat :finding/assertions) (vals (:dossier/findings dossier)))
            comm (communication/compose
                  {:situation-id (:dossier/situation-id dossier)
                   :crime        (:dossier/crime dossier)
                   :findings     (:dossier/findings dossier)
                   :assertions   as}
                  {:at at :hash-fn hash-fn :submitter-did submitter-did :language language})]
        {:drafted? true
         :communication comm
         :dossier (assoc dossier :dossier/dispatch (communication/dispatch-request comm))}))))

(defn status
  "One line an operator can read without opening the dossier. Reports what was
  scanned, not only what passed — `0 assertions` and `all gates green` must not
  render the same way."
  [dossier]
  (let [adm (gov/admit dossier (:dossier/at dossier))
        as  (into [] (mapcat :finding/assertions) (vals (:dossier/findings dossier)))]
    {:situation   (:dossier/situation-id dossier)
     :crime       (:dossier/crime dossier)
     :verdicts    (into {} (map (fn [[k v]] [k (:finding/verdict v)]))
                        (:dossier/findings dossier))
     :assertions  (count as)
     :named       (count (filterv rec/names-individual? as))
     :rebuttal    (select-keys (:dossier/rebuttal dossier) [:ran? :refuted? :grounds])
     :admitted?   (:admitted? adm)
     :refusals    (mapv (juxt :gate/id :refusal/reason) (:refusals adm))}))
