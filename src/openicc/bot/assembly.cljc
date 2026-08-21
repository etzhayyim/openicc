(ns openicc.bot.assembly
  "**締約国会議 bot** — Article 112, the Assembly of States Parties.

  In the Court, the ASP does not decide cases; it provides oversight, elects,
  and sets the rules the organs work under. openicc keeps that separation: this
  bot never evaluates evidence. It answers one question — *is there a quorum of
  independent nodes willing to certify this head?* — and gate G7 will not let a
  named finding out without its answer.

  This is the whole safety argument for an autonomous accusing system, so it is
  worth stating plainly. The bots are autonomous: nobody approves each run. But
  publication is a *consensus act*. A single compromised, misconfigured or
  simply mistaken node can reach `admitted?` in its own process and still
  publish nothing, because 2f+1 signatures over the content address do not
  exist. Autonomy is preserved; unilateral action is not available.

  The quorum arithmetic and certificate verification live in `kotoba-lang/inga`
  (`inga.quorum`, `inga.head/verify-cert`) and are INJECTED here rather than
  reimplemented — a second quorum implementation is a second answer to the same
  question, which is the one thing a consensus plane must not have."
  (:require [openicc.record :as rec]))

(defn byzantine-threshold
  "2f+1 out of n, i.e. `n - floor((n-1)/3)`. Returned rather than assumed so a
  caller can print it next to the signer count; a threshold nobody can see is a
  threshold nobody checks."
  [n]
  (let [f (quot (dec (max n 1)) 3)]
    {:n n :f f :threshold (- n f)}))

(defn ballot
  "One node's vote on a dossier, over the dossier's content address.

  The vote is over the CID, never over the dossier value. A node that signs a
  value has signed whatever that value was in its own memory; a node that signs
  a content address has signed something every other node can recompute."
  [{:keys [signer dossier-cid admitted? refusals at]}]
  {:ballot/signer      signer
   :ballot/dossier-cid dossier-cid
   :ballot/admitted?   admitted?
   :ballot/refusals    (vec (or refusals []))
   :ballot/at          at})

(defn tally
  "Count ballots for one content address.

  Ballots for a *different* CID are counted separately and reported, never
  ignored: divergence means two nodes built different dossiers from what should
  have been the same inputs, and that is a finding about the system which must
  not be swallowed by a filter."
  [dossier-cid ballots]
  (let [{:keys [on-cid off-cid]} (group-by (fn [b] (if (= dossier-cid (:ballot/dossier-cid b))
                                                     :on-cid :off-cid))
                                           ballots)
        on       (vec on-cid)
        yes      (filterv :ballot/admitted? on)
        signers  (into #{} (map :ballot/signer) yes)]
    {:dossier-cid dossier-cid
     :cast        (count ballots)
     :on-cid      (count on)
     :off-cid     (count (vec off-cid))
     :yes         (count yes)
     :signers     signers
     :divergent   (into #{} (map :ballot/dossier-cid) (vec off-cid))}))

(defn certify
  "Ask the injected `quorum-met?` whether the yes-signers carry the quorum, and
  build the certificate gate G7 reads.

  `quorum-met?` is `(fn [signers] boolean)` — supplied by `inga.quorum/met?`
  bound to the configured profile. Refusing to default it is deliberate: a
  default quorum is a quorum nobody chose."
  [{:keys [dossier-cid ballots quorum-met? node-count at]}]
  (when-not (ifn? quorum-met?)
    (throw (ex-info "certify requires an injected quorum-met? predicate"
                    {:openicc/refusal :missing-quorum-predicate})))
  (let [t (tally dossier-cid ballots)
        met? (boolean (quorum-met? (:signers t)))]
    {:cert        (when met?
                    {:cert/dossier-cid dossier-cid
                     :cert/signers     (vec (sort (map str (:signers t))))
                     :cert/at          at})
     :quorum-met? met?
     :signers     (vec (sort (map str (:signers t))))
     :threshold   (byzantine-threshold (or node-count (:cast t)))
     :tally       t}))

(defn attach
  "Put the certificate where the governor looks for it."
  [dossier certification]
  (assoc dossier :dossier/quorum (select-keys certification [:cert :quorum-met? :signers])))

(defn dossier-cid
  "Address the evaluable part of a dossier. Excludes the quorum block itself —
  otherwise every ballot would change the thing being voted on."
  [hash-fn dossier]
  (rec/content-id hash-fn (dissoc dossier :dossier/quorum :dossier/dispatch)))
