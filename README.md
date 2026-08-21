# openicc — 国際刑事裁判所の手続を、自律分散で回す bots

**DID**: `did:web:openicc.etzhayyim.com`
**Namespace**: `openicc.*` · **Lexicons**: `com.etzhayyim.openicc.*`
**ADR**: [ADR-2608220100](https://github.com/com-junkawasaki/root/blob/main/90-docs/adr/2608220100-openicc-autonomous-icc-procedure-bots.edn)
**Status**: R0 — 9 bots 実装済み・14 憲章ゲート実装済み・全ゲート両方向テスト済み。**live 稼働なし・実データなし・一度も送信していない。**

---

openicc は**ローマ規程の分析手続そのもの**を、9 体の bot と 1 体の独立 governor と
合意プレーンとして実装したものです。ICC の 4 機関（裁判部・検察局・書記局・
締約国会議）に弁護を加えた構成を写しており、各 bot は自分の担当条文しか見ません。

| bot | ICC の機関 | 条文 | 何をするか |
|---|---|---|---|
| `examiner` 予備審査 | 検察局 | 15(2) | 公開情報を読み、出典を CID で固定した assertion にする |
| `elements` 構成要件 | 検察局 | 6 / 7 / 8 / 8bis | 事実が chapeau（文脈的要素）を満たすか |
| `jurisdiction` 管轄 | 裁判部 | 11 / 12 / 13 | 時・場所・人・トリガー |
| `admissibility` 受理可能性 | 裁判部 | 17 | 補完性と重大性 — **格付けはここだけ** |
| `interests` 司法の利益 | 検察局 | 53(1)(c) | 手を引く理由があるか |
| `defence` 弁護 | 弁護 | 67 | **上の全部を壊しにいく** |
| `communication` 通報 | — | 15(2) | 検察官への通報を起草する |
| `registry` 書記局 | 書記局 | 43 | block / ledger / anchor の保管 |
| `assembly` 締約国会議 | ASP | 112 | **名指しの公表を許す唯一の quorum** |

## なぜ「自律」と「単独犯にならない」が両立するのか

オーナー判断（2026-08-21）で openicc は、`cloud-itonami/danjo` の憲章とは逆に
**第 17 条の受理可能性を評価し、実在の国家と個人を名指しする第 15 条(2)通報を
起草する**ことになりました。これは重い権限なので、憲章は danjo を継承せず自前で持ち、
ゲートは danjo より**重く**してあります。

「人を告発する自律 bot」に対する構造的な答えは、bot を慎重にすることではありません。
**単独の bot には公表が物理的にできないようにすること**です。

> ゲート **G7** — 名指しの finding は、その content address に対する
> **2f+1 の quorum 証明書**がなければ公表できない。

bot は自律しています（毎回の承認は要りません）。しかし公表は**合意行為**です。
1 台が侵害されても、誤設定でも、単に間違っていても、そのプロセス内で `admitted?` に
到達したうえで**何も公表されません** — 署名が集まらないからです。
自律は保たれ、単独行動は選択肢から消えます。

### 第 15 条(2)は「体制の外からの告発」ではない

ローマ規程は**あらゆる個人・集団・団体**が検察局へ情報を送ることを予定しています
（15(1)-(2)）。検察官はその重大性を分析する義務を負う。つまり通報は、体制が
自ら求めている入力です。openicc の外向きチャネルは**これ 1 本だけ**で、ゲート G8 が
他の全部（報道・当事者・公開リスト）を拒否します。**判断するのは裁判所であり、
openicc は提出するだけです。**

## 14 の憲章ゲート

`openicc.governor` は独立した系統で、答える問いは 1 つだけ — *これは外に出てよいか*。
bot 自身の申告ではなく記録から答えます。

| | ゲート |
|---|---|
| G1 | 第 5 条(1)の閉じた犯罪集合の外を主張できない |
| G2 | 引用する条文は registry で解決し、かつ `:verified` であること |
| G3 | 全 assertion が出典を持つ。**assertion 0 件は `:no-evidence` であって pass ではない** |
| G4 | 自然人を名指しする assertion は**独立した 2 社以上**＋ CID 固定 1 件以上 |
| G5 | 4 つの limb（要件・管轄・受理可能性・利益）が揃い、全て affirmative |
| G6 | 弁護 bot が**実際に走り**、反証に失敗したこと。**未実行は拒否** |
| G7 | 名指しには 2f+1 quorum 証明書 |
| G8 | 送信先は検察局のみ |
| G9 | 格付けは第 17 条の limb で表現されたものだけ。**index・ランキング・順位表は拒否** |
| G10 | 時間的下限（2002-07-01 と当該国の効力発生日の**遅い方**） |
| G11 | 適法収集のみ。侵入・侵害情報・アクセス制御回避・bot 検出回避は拒否 |
| G12 | append-only の系譜。**拒否も記録である** |
| G13 | 第 66 条 — 主張と出典を述べ、有罪を宣告しない |
| G14 | 推論は murakumo-main alias 経由のみ。**どのモデルが出したか記録しない finding は拒否** |

### 証拠の床

全ゲートが SCANNED 件数を返し、**0 件の走査は pass ではなく拒否**です。
このワークスペースが繰り返し支払ってきた失敗が「測れなかった検査が、測って問題が
なかった検査と同じ値を返す」ことなので、ここでは「検査対象が無い」は
`:no-evidence` という**別の答え**になります。

## 分散プレーン — 3 面を混ぜない

```
block   記録そのもの。content address（CID）。不変。
ref     どれが現在か。inga の 2f+1 quorum が裁定する。単一ベンダの条件付き書込みではない。
anchor  head が存在したことの公開コミットメント。Ethereum / Filecoin へ定期的に。
```

判定基準は ADR-2608039000 の**削除テスト**そのものです — *今それを消したとき、
データが失われるか、正しさが壊れるか。* `openicc.chain/validate` はこれを散文ではなく
**設定に対して**適用します（散文の規則は、誰かが急いでいるときまでしか守られない）。

**anchor には主張の本文を書きません。** 書けば、後に誤って名指しされたと判明した人に
ついても撤回不能な告発が公開チェーン上に残ります。第 66 条と両立しません。
anchor するのは head の CID と quorum 証明書だけです。

## 永続性 — 無限ループではない

長寿命の agent は「長時間走る関数」ではありません。`openicc.tick/run-tick` は
**1 回に 1 フェーズだけ**進めて返ります。継続は外側にあります：
lease（`:agent.lease/*`）・tick・budget・checkpoint・crash recovery。

lease は ref プレーン＝ inga quorum に対する CAS なので、2 台が同時に「自分が持って
いる」と信じることができません。これと G7 を合わせると、オーナーが求めた性質に
なります — **bot は複数台で自律的に永続稼働し、そのどの 1 台も単独では告発を
公表できない。**

## 何に基づいて作られているか

- **[CopilotKit/openbot](https://github.com/CopilotKit/openbot)** — *governance gateway*
  という発想。全ツール呼び出しが実行**前**に方針判定を通り、実行後に記録される
  fail-closed な単一強制点。ここでは `openicc.governor` がそれに当たります。
  分離した gateway に CEL で書く代わりに、条文ごとに名前の付いた 14 ゲートを
  両方向テスト付きで持っています。
- **[Hermes Agent (Nous Research)](https://github.com/NousResearch/hermes-agent)** —
  自己ホストで、永続的で、cron で回り続ける自律 agent という形。openicc が採らなかった
  のは無限内部ループで、`tick` の durable outer loop に置き換えています（監査可能・
  有界・再開可能）。
- **cloud-itonami の actors**（`danjo` / `ooyake` / `kurashimori` / `toritsugi`）—
  封じ込め＋独立 governor＋不変台帳、coded registry（`kurashimori` の remedy registry）、
  `:unverified-seed` の正直さ（`ooyake` の atlas）。これが骨格です。
- **[`kotoba-lang/inga`](https://github.com/kotoba-lang/inga)** — 合意プレーン。
  quorum 算術も証明書検証も**再実装していません**。合意プレーンが持ってはいけない
  唯一のものが「同じ問いへの 2 つ目の答え」です。

## 使い方

```bash
nbb run_tests.cljs                          # 中核スイート（依存なし・ネットワーク不要）
clojure -M:test                             # 同じ .cljc を JVM で
clojure -M:inga:test -d test -d test-inga   # 実 inga を通した seam も含めて
clojure -M:lint
```

`src/` は**依存ゼロ**です。禁欲ではなく、CLAUDE.md のランタイム順序
（kotoba wasm > clojurewasm > ClojureScript > nbb > JVM）に従うためです。
既定 classpath に合意プレーンの依存があると、この library は JVM に固定され、
テストにネットワークが要るようになります。`inga` に触れる namespace は
`openicc.chain.inga` **1 本だけ**で、`:inga` alias でしか有効になりません。

```clojure
(require '[openicc.core :as icc])

(-> (icc/build-dossier situation {:at now :inference-endpoint "murakumo-main"})
    (icc/run-defence {})
    (icc/status))
;; => {:assertions 1 :named 0 :admitted? false :refusals [[:G2 :unverified-citation] ...]}
```

## 今日の正直な現在地

- **registry は全件 `:unverified-seed`。** `registry/situations.seed.edn` の事態一覧も
  `openicc.statute` の条文表も、二次情報から seed しただけで、**1 件も検証されて
  いません**。ゲート G2 がそれを公表不能にします。オーナーが指したUNIC のページは
  2016 年時点（締約国 124・事態 10）で既に古く、`:stale-do-not-cite` として
  「何が要求元だったか」の記録としてのみ残してあります。
- **したがって、いま実データで回すと必ず hold になります。** それが正しい挙動で、
  end-to-end テストはまさにそれを固定しています。次の一手は「送る」ことではなく
  **registry を検証すること**です。
- **`.kotoba` 化は未着手。** `jurisdiction` と `admissibility` と `elements` は
  純粋な決定核（decision core）で、CLAUDE.md が `.kotoba` に切り出せと言っている
  形そのものですが、今は `.cljc` です。境界は既に引いてあります — collection の
  組み立てと effect は外、判断だけが中。
- **live 稼働なし。** lease も、実ノードも、実 anchor も配備していません。
  `dispatch-fn` と `anchor-fn` は注入されるもので、既定値はありません
  （既定の送信先は、誰も選んでいない送信先です）。

## 非目標

1. 裁判所ではない。有罪・無罪を判定しない（第 66 条 / G13）。
2. 国家のランキング・index・順位表ではない（G9）。第 17 条は**裁判所の権限**についての
   問いであって、国家についての評点ではない。
3. 被害者支援でも法律相談でもない。
4. 通報以外の公表チャネルを持たない（G8）。名指しの公開リストを作らない。
5. 非公開情報・侵害情報を扱わない（G11）。
6. ICC の公式機関ではない。ICC を名乗らない。
7. 証人・被害者と直接接触しない。保護の仕組みを持たないため。
8. 締約国会議の bot は証拠を評価しない。quorum だけを答える。
