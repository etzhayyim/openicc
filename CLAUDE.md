# CLAUDE.md — openicc

このリポジトリで作業する agent への契約。**README を先に読むこと。**

## 唯一の不変条件

> **governor が拒否する公表・送信・attestation を、openicc は決して行わない。**

`openicc.governor/admit` を迂回する経路を作らない。`dispatch-fn` を
`openicc.tick/run-tick` の外から呼ばない。「テストのために一時的に」も含めて、
governor を通さない送信経路を書かない。

## この repo が danjo と違う理由（読み飛ばさない）

`cloud-itonami/danjo` は憲章で **NON-adjudicating** — 観測はするが格付けはしない。
openicc はオーナー判断（2026-08-21）で**逆**に振られている: 第 17 条の受理可能性を
評価し、実在の国家と個人を名指しする第 15 条(2)通報を起草する。

したがって:

- **danjo の憲章を openicc に引用しない。** 継承していない。openicc は自前の 14 ゲートを
  持ち、そちらが正本。
- **逆に、openicc のゲートを他 actor に持ち出さない。** G7（名指しには quorum）は
  この repo の権限が重いことへの対価であって、一般的な作法ではない。
- danjo の G11「政府をランキングしない」に相当する制約は、openicc では **G9** として
  残っている — 格付けは**第 17 条の limb で表現されたものだけ**。index も順位表も拒否。
  「受理可能性の評価」と「国家の評点」は別のもので、後者を作らない。

## ゲートを触るときの規則

1. **ゲートは両方向を見せてから landed とする。** 通す dossier と、**1 箇所だけ**壊した
   dossier で、**名指しした**ゲートが発火することを実際に見る。2 箇所壊して赤くなっても
   どの検査が効いたかの証拠にならない。
2. **0 件の走査は pass ではない。** 新しいゲートは必ず `:no-evidence` の枝を持つ。
   持たないゲートは、入力が無いときに静かに緑を返す。
3. **集合に畳まない。** 2026-08-22、G14 は全 finding の endpoint を 1 つの集合に畳んで
   いたため、**何も記録しなかった finding が兄弟の陰に隠れて素通り**していた。
   per-item で見る。この誤りはテストが捕まえた。
4. **拒否は記録である。** hold は `registry/entry` を書く。記録を残さない拒否は、
   一度も走らなかったことと区別がつかない。

## ランタイム

`src/` は**依存ゼロの `.cljc`**。これを崩さない。

- 新しい third-party maven / 外部 git 依存を top-level `:deps` に足さない
  （ADR-2608170200 / PreToolUse `jvm-new-surface-guard`）。
- 新しい production `.clj` を置かない。`.cljc` で書く。
- host interop（`.indexOf` 等）を `src/` に書かない。同じコードが JVM・nbb・
  Kotoba/WASM guest で走ることが前提（CLAUDE.md のランタイム順序）。
- `inga` に触れてよいのは `src-inga/openicc/chain/inga.cljc` **だけ**。
  合意プレーンの依存が既定 classpath に載った時点で、この library は JVM に固定される。

```bash
nbb run_tests.cljs                          # 中核（依存なし）
clojure -M:test                             # 同じ .cljc を JVM で
clojure -M:inga:test -d test -d test-inga   # 実 inga の seam 込み
```

**両方走らせること。** 2026-08-22、テスト内の無意味な式を nbb は素通しし JVM だけが
arity エラーで捕まえた。片方のランタイムでしか走っていないスイートは、その
ランタイムについての証拠でしかない。

## registry を「検証済み」にするとき

`openicc.statute` と `registry/situations.seed.edn` は全件 `:unverified-seed` で、
ゲート G2 がそれを公表不能にしている。**これが現在地であり、欠陥ではない。**

検証するときは:

- 出所は**寄託テキストと ICC 自身の公開記録**（`legal.un.org` / `icc-cpi.int`）。
  二次情報（Wikipedia・UNIC ページ・報道）を `:verified` の根拠にしない。
- **1 件ずつ**上げる。ファイル全体を一括で `:verified` にしない。
- 検証したという事実を、検証した日付と突き合わせた URL とともに書く。
  「照合した」とだけ書かない。
- UNIC のページ（オーナーが指した要求元）は 2016 年時点で、締約国 124・事態 10 と
  書いている。**どちらも古い。** `:stale-do-not-cite` のまま置く。

## 数値をこのファイルに書かない

ゲート数・bot 数・テスト件数・締約国数・事態数を、この CLAUDE.md や README の散文に
定数として書き足さない。書けば引用され、引用する側は日付を落とす。
**引き方を書く**（`gov/gates` を数える、`clojure -M:test` を走らせる）。
既に書いてある数値は landing 時点の実測で、疑わしければ測り直す。

## 送信について

外向きチャネルは第 15 条(2)通報 1 本だけ（G8）。それでも:

- **`dispatch-fn` に既定値を作らない。** 既定の送信先は誰も選んでいない送信先。
- 実送信の前に、この repo の live 稼働状態をオーナーに確認する。R0 の現在、
  registry が未検証なので、そもそも governor が通さない。
- 通報の本文は**主張と出典**を述べる。有罪を宣告しない（G13 が文面を検査する）。
