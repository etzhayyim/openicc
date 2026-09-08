(ns openicc.record
  "Records, their provenance, and the content address that names them.

  Pure `.cljc`. No I/O, no crypto, no wall-clock — the hash function, the clock
  and the signer are INJECTED, the same seam `inga.head` uses. That is what
  keeps this library portable to the Kotoba/WASM guest and what keeps two
  replicas from disagreeing about the identity of a record.

  Two ideas do all the work here:

  * **A record is named by its content.** `content-id` is a fold over the
    canonical form, so a record that was edited is a *different* record rather
    than the same record with different contents. Retraction is therefore
    possible and revision-in-place is not.

  * **A claim without a source is not a claim.** `assertion` refuses to build
    an assertion that cites nothing. This is not a lint — in this domain an
    unsourced sentence about a named person is the failure mode, so it is
    unrepresentable rather than discouraged."
  (:require [kotoba.lang.text :as str]))

;; ---------------------------------------------------------------------------
;; Canonical form
;; ---------------------------------------------------------------------------

(defn- canonical
  "Deterministic rendering. Maps are emitted in sorted-key order so that two
  replicas that built the same record by different code paths address it
  identically. `pr-str` on a map is NOT stable across runtimes once it exceeds
  the small-map threshold, which is exactly the class of bug that makes a
  content address quietly stop being one."
  [v]
  (cond
    (map? v)
    (str "{" (str/join "," (for [k (sort-by str (keys v))]
                             (str (canonical k) " " (canonical (get v k)))))
         "}")

    (set? v)
    (str "#{" (str/join "," (map canonical (sort-by str v))) "}")

    (sequential? v)
    (str "[" (str/join "," (map canonical v)) "]")

    :else (pr-str v)))

(defn canonical-bytes
  "The bytes a `hash-fn` is asked to digest. Exposed because a test that cannot
  see what was hashed cannot tell a stable digest from a stable mistake."
  [v]
  (canonical v))

(defn content-id
  "Address `v` under the injected `hash-fn` (bytes -> string).

  `hash-fn` is required and is not defaulted to anything. A default here would
  be a second, weaker identity scheme that works right up until two deployments
  disagree about which one they used."
  [hash-fn v]
  (when-not (ifn? hash-fn)
    (throw (ex-info "openicc.record/content-id requires a hash-fn"
                    {:openicc/refusal :missing-hash-fn})))
  (hash-fn (canonical-bytes v)))

;; ---------------------------------------------------------------------------
;; Sources
;; ---------------------------------------------------------------------------

(defn source
  "A retrievable source, addressed by content.

  `:source/cid` is what makes a citation checkable later: a URL says where the
  bytes were, a CID says what they were. A source recorded by URL alone is
  accepted but marked `:source/pinned? false`, and the governor counts pinned
  and unpinned sources differently (G8)."
  [{:keys [url cid title publisher retrieved-at kind]}]
  (when (str/blank? (str url))
    (throw (ex-info "a source must have a url" {:openicc/refusal :source-without-url})))
  (cond-> {:source/url          (str url)
           :source/title        (some-> title str)
           :source/publisher    (some-> publisher str)
           :source/retrieved-at retrieved-at
           :source/kind         (or kind :open-source)
           :source/pinned?      (boolean (seq (str cid)))}
    (seq (str cid)) (assoc :source/cid (str cid))))

(defn independent-sources
  "How many *distinct publishers* back these sources.

  Counting sources rather than publishers is the mistake this function exists
  to prevent: five outlets reprinting one wire story is one source, and a
  corroboration rule that counts it as five is a rule that corroborates
  nothing. Sources with no publisher recorded each count as their own — we
  cannot show they are the same, and guessing in the direction that makes a
  finding publishable is the wrong direction to guess."
  [sources]
  (count (into #{} (map-indexed (fn [i s]
                                  (or (some-> (:source/publisher s) str/lower str/trim not-empty)
                                      [::anonymous i])))
               sources)))

;; ---------------------------------------------------------------------------
;; Assertions
;; ---------------------------------------------------------------------------

(defn assertion
  "One factual proposition, its sources, and the Statute articles it is offered
  under.

  Refuses to exist without at least one source. `:assertion/confidence` is
  carried but is never load-bearing for publication — the governor's gates read
  source counts and rebuttal outcomes, not a number a language model wrote about
  its own certainty."
  [{:keys [claim sources cites confidence subject-kind]}]
  (when (str/blank? (str claim))
    (throw (ex-info "an assertion must state a claim"
                    {:openicc/refusal :assertion-without-claim})))
  (when (empty? sources)
    (throw (ex-info "an assertion must cite at least one source"
                    {:openicc/refusal :assertion-without-source
                     :assertion/claim (str claim)})))
  {:assertion/claim        (str claim)
   :assertion/sources      (vec sources)
   :assertion/cites        (vec (or cites []))
   :assertion/confidence   confidence
   ;; :individual / :state / :organisation / :none — read by gate G4, which
   ;; treats an assertion naming a natural person more strictly than one about
   ;; an event.
   :assertion/subject-kind (or subject-kind :none)
   :assertion/independent-sources (independent-sources sources)})

(defn names-individual? [a] (= :individual (:assertion/subject-kind a)))

;; ---------------------------------------------------------------------------
;; Findings
;; ---------------------------------------------------------------------------

(defn finding
  "A bot's output. Findings are proposals; nothing here publishes.

  `:finding/verdict` is deliberately three-valued. `:indeterminate` is a real
  answer and the most common correct one — a bot forced to choose between
  `yes` and `no` when it could not measure will choose, and a pipeline that
  cannot say `I could not tell` accumulates silence as agreement."
  [{:keys [bot kind verdict rationale assertions cites situation-id at]}]
  (when-not (contains? #{:affirmative :negative :indeterminate} verdict)
    (throw (ex-info "verdict must be :affirmative, :negative or :indeterminate"
                    {:openicc/refusal :bad-verdict :verdict verdict})))
  {:finding/bot          bot
   :finding/kind         kind
   :finding/verdict      verdict
   :finding/rationale    (str rationale)
   :finding/assertions   (vec (or assertions []))
   :finding/cites        (vec (or cites []))
   :finding/situation-id situation-id
   :finding/at           at
   :finding/proposal?    true})

(defn affirmative? [f] (= :affirmative (:finding/verdict f)))

(defn address
  "Attach the content address. Done last, over the finished record, so the
  address covers everything the record says."
  [hash-fn record]
  (assoc record :record/cid (content-id hash-fn (dissoc record :record/cid))))
