(ns openicc.chain
  "The decentralised planes, and the seams openicc reaches them through.

  Nothing in this namespace performs I/O or crypto. It defines the shape of the
  three planes and refuses configurations that only *look* decentralised — the
  wiring itself lives in `openicc.chain.inga` (JVM/ClojureScript adapter),
  which is the only place `inga` is required.

  ## The rule this namespace enforces

  ADR-2608039000: on a path that claims to be decentralised, no single-vendor
  primitive may be a *premise*. The test is deletion — remove it now; is data
  lost, or is correctness broken? If yes, it is a premise and it is refused
  here. If it only gets slower because the answer can be rebuilt from
  content-addressed blocks, it is a cache and it is fine.

  `validate` applies that test to a configuration rather than to prose, because
  a rule that lives only in a document is a rule that is followed until someone
  is in a hurry."
  (:require [clojure.string :as str]))

(def planes
  {:blocks {:plane/role "the records themselves, addressed by content"
            :plane/required-capabilities #{:immutable-blocks :cid-addressed-read}
            :plane/acceptable [:ipfs :b2 :r2 :s3 :filecoin :datalad-annex]
            :plane/note "conditional writes are NOT required here"}
   :refs   {:plane/role "which record is current"
            :plane/required-capabilities #{:linearizable-ref :quorum-decided}
            :plane/acceptable [:inga]
            :plane/note
            "the 2f+1 quorum certificate IS the conditional write, which is why
             no host-side compare-and-set appears on this path"}
   :anchor {:plane/role "a public commitment that a head existed"
            :plane/required-capabilities #{:public-verifiable :append-only}
            :plane/acceptable [:ethereum :filecoin]
            :plane/note
            "commitment only. The allegation text is never written to a public
             chain: Article 66 is not compatible with an allegation that cannot
             be withdrawn about a person later shown to have been wrongly named."}})

(def forbidden-as-premise
  "Named, because ADR-2608039000 names them. A cache is fine; a premise is not."
  #{:d1 :single-region-sql :vendor-conditional-write :durable-object-storage})

(defn validate
  "Check a plane configuration. Returns `{:ok? :problems}`.

  Returns a *problem list*, not a boolean, so a refusal can say which plane and
  why. `:ok? false` with an empty problem list is impossible by construction —
  that combination is the shape of a check that could not run."
  [{:keys [blocks refs anchor] :as config}]
  (let [problems
        (cond-> []
          (nil? blocks)
          (conj {:plane :blocks :problem :not-configured})

          (nil? refs)
          (conj {:plane :refs :problem :not-configured
                 :note "without a ref plane there is no consensus, and gate G7 can never pass"})

          (and blocks (forbidden-as-premise (:provider blocks)))
          (conj {:plane :blocks :problem :single-vendor-premise
                 :provider (:provider blocks)})

          (and refs (forbidden-as-premise (:provider refs)))
          (conj {:plane :refs :problem :single-vendor-premise
                 :provider (:provider refs)
                 :note "a vendor conditional-write decides the ref; deleting it breaks correctness"})

          (and refs (not (:quorum refs)))
          (conj {:plane :refs :problem :no-quorum-profile
                 :note "the quorum profile must be chosen explicitly; a default quorum is a quorum nobody chose"})

          (and anchor (not (contains? (set (:plane/acceptable (:anchor planes))) (:chain anchor))))
          (conj {:plane :anchor :problem :unknown-chain :chain (:chain anchor)})

          (and anchor (:publish-content? anchor))
          (conj {:plane :anchor :problem :content-on-public-chain
                 :note "anchor the head CID and its certificate, never the allegation text"}))]
    {:ok? (empty? problems)
     :problems problems
     :checked (count (keys (select-keys config [:blocks :refs :anchor])))}))

(defn describe
  "One-line summary for the operator log."
  [config]
  (let [{:keys [ok? problems checked]} (validate config)]
    (if ok?
      (str "chain OK — " checked " planes: blocks=" (:provider (:blocks config))
           " refs=" (:provider (:refs config))
           " anchor=" (:chain (:anchor config)))
      (str "chain REFUSED — " (str/join "; "
                                        (for [p problems]
                                          (str (name (:plane p)) "/" (name (:problem p)))))))))

(def default-config
  "What the owner chose on 2026-08-21: inga for refs, content-addressed blocks,
  and periodic anchoring to a public L1.

  Named a *default* and not a constant: it is a starting configuration, and
  `validate` is what actually decides whether a deployment is sound."
  {:blocks {:provider :ipfs   :fallback :b2}
   ;; :head-count is correct here because openicc's validator set is MANAGED —
   ;; nodes are enrolled by the assembly. Open the admission and this must
   ;; become :stake-weighted; see openicc.chain.inga/quorum-predicate.
   :refs   {:provider :inga   :quorum :head-count}
   :anchor {:chain :ethereum  :every-n 32 :publish-content? false}})
