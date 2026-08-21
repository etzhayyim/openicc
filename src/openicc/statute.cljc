(ns openicc.statute
  "The Rome Statute as data — the coded registry every openicc bot resolves against.

  This namespace holds NO judgement. It holds the *tests the Statute itself
  states*, keyed by article, each carrying its own provenance and verification
  status. A bot that wants to say `this fact satisfies Article 7(1)(a)` must
  name the entry here, and the entry must be `:verified` before any named
  finding derived from it may be published (gate G8 / G14).

  Why a registry and not prose in each bot: kurashimori's remedy registry
  learned this the expensive way — a statutory number copied into five call
  sites drifts in four of them, and a wrong number in this domain is not a
  cosmetic defect. One table, cited by article, verified once.

  Everything here is `:unverified-seed` until a human or an `examiner` run
  reconciles it against the depositary text at
  https://legal.un.org/icc/statute/99_corr/cstatute.htm. `unverified-seed`
  entries are readable and reasonable-about; they are NOT publishable."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Provenance
;; ---------------------------------------------------------------------------

(def ^:const statute-source
  "The depositary text these entries were seeded from."
  {:source/title "Rome Statute of the International Criminal Court"
   :source/url   "https://legal.un.org/icc/statute/99_corr/cstatute.htm"
   :source/adopted "1998-07-17"
   :source/in-force "2002-07-01"
   :source/note
   "Seeded 2026-08-21 from the UN depositary text and the UNIC Japan summary
    (https://www.unic.or.jp/activities/international_law/icc/). The UNIC page
    is dated to 2016 and its figures (124 States Parties, 10 situations) are
    STALE — it is recorded as the requesting source, never as the authority
    for a number."})

(defn seed
  "Stamp an entry as an unverified seed. Verification is a separate act with a
  separate record; nothing here may promote itself."
  [m]
  (assoc m :registry/status :unverified-seed
           :registry/source (:source/url statute-source)))

;; ---------------------------------------------------------------------------
;; Article 5 — the four crimes
;; ---------------------------------------------------------------------------

(def crimes
  "Article 5(1). The closed set. A bot that reports a crime kind outside this
  set is refused by the governor, not merely warned — the Court's subject-matter
  jurisdiction is exhaustive and an open enum here would let a bot invent one."
  (mapv seed
        [{:crime/id :genocide
          :crime/article "5(1)(a)"
          :crime/definition-article "6"
          :crime/ja "集団殺害犯罪"
          :crime/en "Genocide"
          :crime/chapeau
          "Acts committed with intent to destroy, in whole or in part, a
           national, ethnical, racial or religious group, as such."
          :crime/requires-special-intent? true}
         {:crime/id :crimes-against-humanity
          :crime/article "5(1)(b)"
          :crime/definition-article "7"
          :crime/ja "人道に対する罪"
          :crime/en "Crimes against humanity"
          :crime/chapeau
          "Acts committed as part of a widespread or systematic attack directed
           against any civilian population, with knowledge of the attack."
          :crime/requires-special-intent? false}
         {:crime/id :war-crimes
          :crime/article "5(1)(c)"
          :crime/definition-article "8"
          :crime/ja "戦争犯罪"
          :crime/en "War crimes"
          :crime/chapeau
          "Grave breaches of the Geneva Conventions of 12 August 1949 and other
           serious violations of the laws and customs applicable in
           international or non-international armed conflict."
          :crime/requires-armed-conflict? true
          :crime/requires-special-intent? false}
         {:crime/id :aggression
          :crime/article "5(1)(d)"
          :crime/definition-article "8bis"
          :crime/ja "侵略犯罪"
          :crime/en "Crime of aggression"
          :crime/chapeau
          "The planning, preparation, initiation or execution, by a person in a
           position effectively to exercise control over or to direct the
           political or military action of a State, of an act of aggression
           which, by its character, gravity and scale, constitutes a manifest
           violation of the Charter of the United Nations."
          :crime/leadership-clause? true
          :crime/activated "2018-07-17"
          :crime/note
          "Jurisdiction over aggression is NOT symmetric with the other three:
           the Kampala amendments carve out nationals and territory of States
           that have not ratified them, and a Security Council referral follows
           a different route (Art. 15bis / 15ter). A bot that treats aggression
           like Article 7 will be wrong about who is reachable."}]))

(def crime-ids (into #{} (map :crime/id) crimes))

(defn crime [id] (first (filter #(= id (:crime/id %)) crimes)))

;; ---------------------------------------------------------------------------
;; Articles 11-13 — when the Court may act at all
;; ---------------------------------------------------------------------------

(def temporal
  (seed {:test/id :temporal
         :test/article "11"
         :test/ja "時間的管轄"
         :test/rule
         "The Court has jurisdiction only with respect to crimes committed
          after the entry into force of the Statute (2002-07-01). For a State
          that becomes a Party afterwards, only crimes committed after entry
          into force for that State, unless it has made an Article 12(3)
          declaration."
         :test/floor "2002-07-01"}))

(def preconditions
  "Article 12(2). Either limb suffices. Article 12(3) lets a non-Party accept
  jurisdiction ad hoc."
  (mapv seed
        [{:precondition/id :territorial
          :precondition/article "12(2)(a)"
          :precondition/rule
          "The State on the territory of which the conduct occurred (or, for
           conduct aboard a vessel or aircraft, the State of registration) is a
           State Party or has accepted jurisdiction."}
         {:precondition/id :nationality
          :precondition/article "12(2)(b)"
          :precondition/rule
          "The State of which the person accused is a national is a State Party
           or has accepted jurisdiction."}
         {:precondition/id :ad-hoc-declaration
          :precondition/article "12(3)"
          :precondition/rule
          "A State not a Party may, by declaration lodged with the Registrar,
           accept the exercise of jurisdiction with respect to the crime in
           question."}]))

(def triggers
  "Article 13. How a situation reaches the Prosecutor. The trigger decides which
  preconditions apply — a Security Council referral under 13(b) does NOT need
  Article 12(2) to be satisfied, and a bot that applies 12(2) to a 13(b)
  situation will wrongly report `no jurisdiction`."
  (mapv seed
        [{:trigger/id :state-referral
          :trigger/article "13(a)"
          :trigger/via "14"
          :trigger/ja "締約国による付託"
          :trigger/requires-art-12-2? true
          :trigger/requires-ptc-authorisation? false}
         {:trigger/id :security-council-referral
          :trigger/article "13(b)"
          :trigger/ja "安全保障理事会による付託"
          :trigger/requires-art-12-2? false
          :trigger/requires-ptc-authorisation? false
          :trigger/note
          "Acting under Chapter VII. This is the only route that reaches the
           territory and nationals of non-Parties without their consent."}
         {:trigger/id :proprio-motu
          :trigger/article "13(c)"
          :trigger/via "15"
          :trigger/ja "検察官の職権による捜査開始"
          :trigger/requires-art-12-2? true
          :trigger/requires-ptc-authorisation? true
          :trigger/note
          "Article 15(3): the Prosecutor must obtain Pre-Trial Chamber
           authorisation before opening an investigation. This is the route an
           Article 15(2) communication feeds."}]))

(def trigger-ids (into #{} (map :trigger/id) triggers))

(defn trigger [id] (first (filter #(= id (:trigger/id %)) triggers)))

;; ---------------------------------------------------------------------------
;; Article 15 — the channel openicc actually uses
;; ---------------------------------------------------------------------------

(def article-15
  (seed {:test/id :article-15
         :test/article "15"
         :test/ja "検察官の職権 / 情報提供"
         :test/rule
         "15(1) The Prosecutor may initiate investigations proprio motu on the
          basis of information on crimes within the jurisdiction of the Court.
          15(2) The Prosecutor shall analyse the seriousness of the information
          received, and may seek additional information from States, organs of
          the United Nations, intergovernmental or non-governmental
          organizations, or other reliable sources.
          15(3) If the Prosecutor concludes there is a reasonable basis to
          proceed, a request for authorisation is submitted to the Pre-Trial
          Chamber.
          15(6) If the Prosecutor concludes the information does not constitute
          a reasonable basis, those who provided it shall be informed; this does
          not preclude consideration of further information on the same
          situation in the light of new facts or evidence."
         :test/who-may-submit
         "Any individual, group or organization. This is the mechanism the
          treaty itself provides — an Article 15(2) communication is not an
          accusation made outside the system, it is an input the system asks
          for. openicc's outbound channel is this and only this."
         :test/channel
         {:otp/portal "OTPLink"
          :otp/post "Evidence and Discovery Management Unit (EDMU), Office of the Prosecutor, Post Office Box 19519, 2500 CM The Hague, The Netherlands"
          :otp/languages ["en" "fr"]
          :otp/also-official ["ar" "zh" "ru" "es"]}}))

;; ---------------------------------------------------------------------------
;; Article 17 — complementarity and gravity (the "rating" the owner asked for)
;; ---------------------------------------------------------------------------

(def admissibility-limbs
  "Article 17(1). Each limb makes a case INADMISSIBLE. Note the polarity: the
  Statute is written as a list of reasons the Court must stand down, not as a
  list of reasons to proceed. A scorer that inverts this reads
  `no national proceedings` as a positive finding about a State, which it is
  not — it is a finding about the Court's own competence."
  (mapv seed
        [{:limb/id :national-investigation
          :limb/article "17(1)(a)"
          :limb/inadmissible-when
          "The case is being investigated or prosecuted by a State which has
           jurisdiction over it, UNLESS that State is unwilling or unable
           genuinely to carry out the investigation or prosecution."
          :limb/escape-hatch :unwilling-or-unable}
         {:limb/id :national-decision-not-to-prosecute
          :limb/article "17(1)(b)"
          :limb/inadmissible-when
          "The case has been investigated by a State with jurisdiction and that
           State has decided not to prosecute, UNLESS the decision resulted from
           unwillingness or inability genuinely to prosecute."
          :limb/escape-hatch :unwilling-or-unable}
         {:limb/id :ne-bis-in-idem
          :limb/article "17(1)(c)"
          :limb/cross-ref "20(3)"
          :limb/inadmissible-when
          "The person has already been tried for the conduct, and a trial by the
           Court is not permitted under Article 20(3)."}
         {:limb/id :gravity
          :limb/article "17(1)(d)"
          :limb/inadmissible-when
          "The case is not of sufficient gravity to justify further action by
           the Court."}]))

(def unwilling-indicia
  "Article 17(2). The closed list. `Unwilling` is not an impression — the
  Statute enumerates what may found it, and openicc may cite nothing else."
  (mapv seed
        [{:indicium/id :shielding
          :indicium/article "17(2)(a)"
          :indicium/rule
          "The proceedings were or are being undertaken for the purpose of
           shielding the person from criminal responsibility."}
         {:indicium/id :unjustified-delay
          :indicium/article "17(2)(b)"
          :indicium/rule
          "There has been an unjustified delay in the proceedings which in the
           circumstances is inconsistent with an intent to bring the person to
           justice."}
         {:indicium/id :not-independent-or-impartial
          :indicium/article "17(2)(c)"
          :indicium/rule
          "The proceedings were not or are not being conducted independently or
           impartially, and were or are being conducted in a manner which, in
           the circumstances, is inconsistent with an intent to bring the person
           to justice."}]))

(def unable-indicia
  (mapv seed
        [{:indicium/id :total-or-substantial-collapse
          :indicium/article "17(3)"
          :indicium/rule
          "Due to a total or substantial collapse or unavailability of its
           national judicial system, the State is unable to obtain the accused
           or the necessary evidence and testimony or otherwise unable to carry
           out its proceedings."}]))

(def gravity-factors
  "Not in the Statute text — these are the OTP's own stated policy factors for
  Article 17(1)(d). They are recorded as OTP policy, NOT as statutory law, and
  a finding that cites them must say so (gate G8)."
  (mapv #(assoc (seed %) :registry/authority :otp-policy)
        [{:factor/id :scale       :factor/ja "規模"}
         {:factor/id :nature      :factor/ja "性質"}
         {:factor/id :manner      :factor/ja "態様"}
         {:factor/id :impact      :factor/ja "影響"}]))

;; ---------------------------------------------------------------------------
;; Article 53 — reasonable basis, and the interests of justice
;; ---------------------------------------------------------------------------

(def article-53
  (seed {:test/id :article-53
         :test/article "53(1)"
         :test/ja "捜査の開始"
         :test/limbs
         [{:limb/id :reasonable-basis :limb/article "53(1)(a)"
           :limb/rule "There is a reasonable basis to believe that a crime
                       within the jurisdiction of the Court has been or is being
                       committed."}
          {:limb/id :admissibility :limb/article "53(1)(b)"
           :limb/rule "The case is or would be admissible under Article 17."}
          {:limb/id :interests-of-justice :limb/article "53(1)(c)"
           :limb/rule "Taking into account the gravity of the crime and the
                       interests of victims, there are nonetheless substantial
                       reasons to believe that an investigation would not serve
                       the interests of justice."}]
         :test/note
         "53(1)(c) is negatively framed — it is a reason to STOP, not a hurdle
          to clear. A bot that requires an affirmative `interests of justice`
          finding before proceeding has inverted the Statute and will silently
          suppress every situation for which nobody wrote a positive case."}))

;; ---------------------------------------------------------------------------
;; Lookup
;; ---------------------------------------------------------------------------

(def ^:private by-article
  (into {}
        (for [e (concat crimes triggers preconditions admissibility-limbs
                        unwilling-indicia unable-indicia
                        [temporal article-15 article-53])
              :let [a (or (:crime/article e) (:trigger/article e)
                          (:precondition/article e) (:limb/article e)
                          (:indicium/article e) (:test/article e))]
              :when a]
          [a e])))

(defn by-article-ref
  "Resolve a citation like \"17(2)(a)\" to its registry entry, or nil.
  Returning nil is the point: a bot citing an article this registry does not
  carry must not be able to publish, and the governor reads exactly this."
  [article]
  (get by-article (str/trim (str article))))

(defn verified?
  "An entry is publishable only when it has been reconciled against the
  depositary text. Nothing in this file is verified yet, and saying so is the
  honest state — a registry that seeds itself as verified is a registry that
  has never been checked."
  [e]
  (= :verified (:registry/status e)))

(defn unverified-citations
  "Every article reference in `citations` that this registry cannot vouch for.
  Empty means publishable as far as G14 is concerned; non-empty is the refusal
  reason, so the caller can name it rather than say `blocked`."
  [citations]
  (vec (remove #(some-> (by-article-ref %) verified?) citations)))
