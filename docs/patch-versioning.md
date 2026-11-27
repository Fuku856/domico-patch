# パッチバージョン仕様

設定画面に表示する domico-patch 自身のバージョンを、**Conventional Commits から
自動算出**する仕組みの仕様。番号の決定は [`scripts/version.py`](../scripts/version.py)
が単一の真実の源（SSOT）で、git-cliff は CHANGELOG 整形のみ担当する。

関連: [`scripts/version.py`](../scripts/version.py) /
[`.github/workflows/release.yml`](../.github/workflows/release.yml) /
[`cliff.toml`](../cliff.toml)

---

## 2つのバージョン軸

このプロジェクトには独立した2つの「バージョン」がある。混同しないこと。

| 軸 | 例 | 意味 | 決め方 |
|----|----|------|--------|
| **パッチ版** | `v0.3.0` | domico-patch 自身のリリース番号 | Conventional Commits から自動算出 |
| **ベースアプリ版** | `1.5.4` | どの公式 Domico ビルドに当てたか | 公式 APK の `versionName`（CI が取得） |

設定画面にはこの両方を併記する：

```
v0.3.0 / base v1.5.4
```

「どの公式バージョンに対応した、パッチの何版か」が一目で分かる。

---

## 表示形式

[`version.py`](../scripts/version.py) が組み立てる文字列。チャンネルで2形態。

| チャンネル | 形式 | 例 | 用途 |
|-----------|------|----|------|
| `release` | `v{X.Y.Z} / base v{app}` | `v0.3.0 / base v1.5.4` | 本番ビルド（`patch.yml` / `release.yml`） |
| `dev` | `v{X.Y.Z}-dev.{N}+g{sha}[.dirty] / base v{app}` | `v0.4.0-dev.2+g7afd66a2 / base v1.5.4` | dev プレリリース（`dev-prerelease.yml`） |

- `-dev.{N}`: セムバーの**プレリリース識別子**。同じ次版に対する dev ビルドの連番。
  `0.4.0-dev.1 < 0.4.0-dev.2 < 0.4.0` という順序が成り立つ（[プレリリース部分は
  リリース版より小さい](https://semver.org/lang/ja/#spec-item-9)）。
- `+g{sha}`: dev ビルドに短縮コミット SHA を付け、追跡可能にする（ビルドメタデータ）。
- `.dirty`: 未コミットの変更があるローカルビルドのみ付与（CI のクリーン checkout では付かない）。
- `base` はビルド時に `--app-version` で渡された公式 `versionName`。未指定なら ` / base ...` を省略。

---

## バージョン算出ルール

[`version.py`](../scripts/version.py) は次の手順で**次版**を決める。

1. **基準（baseline）**: `patch-v*` タグのうち最大セムバー。無ければ `0.0.0`。
2. **増分（bump）**: 基準タグ以降のコミットメッセージ種別から、最も強い増分を採用。

| コミット | 増分 | 例: `0.3.0` → |
|----------|------|----------------|
| `feat!: …` / 本文に `BREAKING CHANGE` | **major** | `1.0.0` |
| `feat: …` | **minor** | `0.4.0` |
| `fix:` / `perf:` / `refactor:` | **patch** | `0.3.1` |
| `docs:` / `ci:` / `chore:` など のみ | 据え置き | `0.3.0` |

増分が複数該当する場合は最大のものを採用（major > minor > patch）。
基準タグ以降にリリース対象コミットが無ければ据え置き＝新リリースなし。

> **1リリース = 1 bump**（重要）
>
> 適用するのは「最も強いレベルを**1回だけ**」であって、コミット本数分ではない。
> このリポジトリは **main へのマージ 1 回 = 1 リリース**なので、`feat` が 3 本
> 入ったマージでも版は minor 1 段だけ進む（`0.5.3` → `0.6.0`、`0.8.2` ではない）。
> セムバーの番号は「そのリリースが含む変更の**重さ**」を表すものであって、
> コミット数を表すものではないため。ReVanced など Android のパッチ系リポジトリが
> 使う semantic-release と同じ刻み方。
>
> この方式では **dev→main を squash しても最終的な版はずれない**
> （squash 後のメッセージに `feat:` / `fix:` 等の種別が残っていればよい）。

### dev プレリリースの連番

main にマージする前の dev ビルドは、版番号を進めるのではなく**プレリリース連番**で
区別する。`N` は既存の `patch-v{X.Y.Z}-dev.*` タグの最大 + 1（無ければ 1）。

```
patch-v0.6.0-dev.1  ← dev で 1 回目のプレリリース
patch-v0.6.0-dev.2  ← コミットを積んで 2 回目
patch-v0.6.0-dev.3
patch-v0.6.0        ← main マージ＝本リリース（必ずここに収束する）
```

ビルドごとに別タグが残るので Releases 一覧に履歴が並び、かつ本リリースの番号は
dev で何回ビルドしたかに左右されない。`patch-v*-dev.*` は基準タグとして数えられない
（[`version.py`](../scripts/version.py) の `_TAG_RE` が完全一致で弾く）ため、
dev 側のビルド回数が本番の版を汚すこともない。

> **初期版**: タグが無い状態では全履歴を走査する。現在の履歴には破壊的変更が
> 無く `feat:` を含むため minor 算出で **`0.1.0`** になる。初回 main リリースで
> `release.yml` が `patch-v0.1.0` を自動作成し、以降はそのタグが基準になる。

---

## タグ体系

| タグ | 例 | 何を表すか | Release | 誰が打つか |
|------|----|-----------|---------|-----------|
| `v{versionName}-{N}` | `v1.5.6-1000` | **配布ビルド**（公式版 + パッチ版の組）。Obtainium が追うのはこれだけ | APK 付き・Latest | `patch.yml`（公式更新時）/ `release.yml`（パッチ版更新時） |
| `v{versionName}-0` | `v1.5.6-0` | 公式ミラー（未加工・公式署名） | Pre-release | `patch.yml` |
| `patch-v{X.Y.Z}` | `patch-v0.10.0` | **パッチ版**の確定（CHANGELOG 境界） | 作らない（git タグのみ） | `release.yml`（main push 時） |
| `patch-v{X.Y.Z}-dev.{N}` | `patch-v0.6.0-dev.2` | dev プレリリース（パッチ版 X.Y.Z の検証）| Pre-release | `dev-prerelease.yml` |

`N` はパッチ版のビルド番号 `major*10000 + minor*100 + patch`（`0.10.0` → `1000`、
`version.py --build-number`）。公式版とパッチ版のどちらが上がっても別タグになり、
CHANGELOG はそのビルドの Release 本文に入る。

`version.py` と `cliff.toml` はバージョン境界として **`patch-v{X.Y.Z}` のみ**を見る
（配布ビルド・公式ミラー・dev プレリリースのタグは無視する）。どちらも
`^patch-v[0-9]+\.[0-9]+\.[0-9]+$` の完全一致で判定する。

### Obtainium との関係
タグをこの形にしているのは、Obtainium（1.7.x で確認）の既定設定で更新を正しく検知させるため。

- **先頭の `v{versionName}`**: インストール済み APK の versionName は公式と同じ（例 `1.5.6`）。
  Obtainium はタグ中で最初に出てくる `X.Y.Z` をこれと照合するので、ここが一致していないと
  「更新あり」が出続けたり（旧 `patch-v0.9.2`）、一度も出なかったりする。
- **`-{N}` が 1 つの整数**: Obtainium は `X.Y.Z-整数` を数値で比べて並べるので、同じ公式版でも
  パッチ版順に並ぶ。`v1.5.6-0.10.0` のような形だと同点になり、古い Release が選ばれる。
- **公式ミラーは `-0` + Pre-release**: 同じ公式版のパッチ版より必ず下に並び、かつ Obtainium の
  既定（Pre-release を無視）で選ばれない。パッチが当たらない新公式版が出ても、未パッチ版は掴まない。
- 旧形式 `v1.5.6-patch` / `v1.5.6-official` は同じ公式版の新タグと同点になり、古い方が選ばれるため
  Release を削除した（1.5.4 系と `patch-v0.x` は新タグより必ず下に並ぶので残している）。

> 旧 `cliff.toml` の `tag_pattern = "patch-v[0-9]*"` は、git-cliff 2.x では
> glob ではなく**部分一致の正規表現**として解釈されるため `patch-v0.6.0-dev` にも
> マッチしてしまい、CHANGELOG に `## [0.6.0-dev]` のような dev 節が混入していた。
> 現在は `^...$` で固定済み。

---

## 仕組み（ビルド時の流れ）

```
version.py（番号算出）
   │  format_version(channel, app_version) → "v0.3.0 / base v1.5.4"
   ▼
build.py  --channel {release,dev} --app-version <vn>
   │  --patch-version "<上の文字列>"
   ▼
patch_apk.py  --patch-version <文字列>
   │
   ▼
patch_smali.py  → PatchInfo.smali の VERSION フィールドを上書き
   │
   ▼
設定ダイアログ（CREDIT + VERSION を表示）
```

`--patch-version` を明示すればそれが優先される。未指定なら build.py が
`version.py` から自動算出する。

---

## CI フロー

| ワークフロー | チャンネル | 役割 |
|--------------|-----------|------|
| [`patch.yml`](../.github/workflows/patch.yml) | `release` | 公式更新を検知して本番 `.apk` をビルドし `v{公式版}-{N}` で Release。公式ミラー `v{公式版}-0` も作る |
| [`dev-prerelease.yml`](../.github/workflows/dev-prerelease.yml) | `dev` | dev のパッチを手動でプレリリース（bump あり or `force=true` 時のみビルド） |
| [`release.yml`](../.github/workflows/release.yml) | — | main push 時に版算出 → CHANGELOG → `patch-v*` タグ → APK を `v{公式版}-{N}` で Release |

`release.yml` の動作：

1. `version.py --number-only --print-bumped` で次版と増分有無を取得。
2. 増分が無い（`feat`/`fix` 等が前回タグ以降に無い）か、同バージョンのタグが既存ならスキップ。
3. git-cliff で `CHANGELOG.md` を生成。
4. `CHANGELOG.md` を `chore(release): … [skip ci]` でコミットし、`patch-v{X.Y.Z}`
   タグを作成して push。
5. `build_apk` ジョブが公式最新版にパッチを当てた `.apk` をビルドし、
   `v{公式版}-{N}` の Release として公開する（CHANGELOG 当該節を本文に）。

> **`build_apk` の実行条件**: 通常は `bumped==1 && exists==false`（新規リリース確定時のみ）。
> `workflow_dispatch` で既存の `patch-vX.Y.Z` タグを指定すれば、そのタグのコードで再ビルドもできる。
> `docs:`/`ci:`/`chore:` のみのコミットは `bumped==0` になり、このジョブもスキップされる。

> **重要**: いずれのワークフローも checkout は `fetch-depth: 0` + `fetch-tags`。
> 版算出に全履歴とタグが必要なため（shallow clone だと `0.0.0` になる）。

---

## 運用ガイド

### 版を上げる
**コミットメッセージを Conventional Commits で書くだけ**。版上げの手動操作は不要。

| やりたいこと | コミット例 | そのリリースの結果 |
|--------------|-----------|------|
| バグ修正 | `fix: ロード遮断の誤判定を修正` | patch +1 |
| 機能追加 | `feat: 設定にダークモードを追加` | minor +1 |
| 互換性破壊 | `feat!: 設定キーを刷新` | major +1 |
| 文書のみ | `docs: README 更新` | 据え置き |

「結果」は**リリース単位**の話で、コミット単位ではない。1 回のリリースに `feat` が
3 本と `fix` が 2 本入っていても、上がるのは minor 1 段だけ（`0.5.3` → `0.6.0`）。

dev に積んだコミットが **main にマージされた時点**で `release.yml` が版を確定する
（dev→main マージ＝パッチリリース）。

### 番号を手動で固定したい
ビルド時に上書きできる：

```bash
python scripts/build.py … --patch-version "v1.0.0 / base v1.5.4"
```

### 確認用コマンド
```bash
python scripts/version.py                              # release 表示
python scripts/version.py --channel dev --app-version 1.5.4
python scripts/version.py --number-only --print-bumped # 次版 + 増分有無(1/0)
python scripts/version.py --dev-seq                    # 次の dev プレリリース連番 N
python scripts/version.py --build-number 0.10.0        # Release タグ v{公式版}-{N} の N (1000)
```

---

## 注意点

- **ブランチ保護**: `release.yml` は main に CHANGELOG コミットとタグを push する。
  main が bot push を拒否する保護設定の場合は、github-actions の push を許可する。
- **再帰実行**: CHANGELOG コミットは `[skip ci]` 付き、かつ `GITHUB_TOKEN` の push は
  ワークフローを再トリガーしないため、無限ループにはならない。
- **git-cliff**: CHANGELOG 整形は [`orhun/git-cliff-action`](https://github.com/orhun/git-cliff-action)
  を CI で使用（ローカルには不要）。番号決定には関与しない。
- **文字コード**: `version.py` は git 出力を明示的に UTF-8 で読む（Windows の cp932 で
  日本語コミットメッセージが壊れるのを回避）。
