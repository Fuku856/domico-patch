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
| `release` | `v{X.Y.Z} / base v{app}` | `v0.3.0 / base v1.5.4` | 本番ビルド（`patch.yml`） |
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

| タグ | 例 | 何を表すか | 誰が打つか |
|------|----|-----------|-----------|
| `patch-v{X.Y.Z}` | `patch-v0.3.0` | **パッチ版**の確定（CHANGELOG 境界） | `release.yml`（main push 時） |
| `v{versionName}-patch` | `v1.5.4-patch` | **ベース追従**ビルドの Release | `patch.yml`（公式更新検知時） |
| `patch-v{X.Y.Z}-dev.{N}` | `patch-v0.6.0-dev.2` | dev プレリリース（パッチ版 X.Y.Z の検証）| `dev-prerelease.yml` |

`version.py` と `cliff.toml` はバージョン境界として **`patch-v{X.Y.Z}` のみ**を見る
（ベース追従タグと dev プレリリースタグは無視する）。どちらも
`^patch-v[0-9]+\.[0-9]+\.[0-9]+$` の完全一致で判定する。

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
| [`patch.yml`](../.github/workflows/patch.yml) | `release` | 公式更新を検知して本番 `.apks` をビルド・Release |
| [`dev-prerelease.yml`](../.github/workflows/dev-prerelease.yml) | `dev` | dev のパッチを手動でプレリリース（bump あり or `force=true` 時のみビルド） |
| [`release.yml`](../.github/workflows/release.yml) | — | main push 時に版算出 → CHANGELOG → `patch-v*` タグ + Release + APK 添付 |

`release.yml` の動作：

1. `version.py --number-only --print-bumped` で次版と増分有無を取得。
2. 増分が無い（`feat`/`fix` 等が前回タグ以降に無い）か、同バージョンのタグが既存ならスキップ。
3. git-cliff で `CHANGELOG.md` を生成。
4. `CHANGELOG.md` を `chore(release): … [skip ci]` でコミットし、`patch-v{X.Y.Z}`
   タグを作成して push。
5. GitHub Release を作成（CHANGELOG 当該節をノートに）。
6. `build_apk` ジョブが公式最新版にパッチを当てた `.apks` をビルドし、`patch-v{X.Y.Z}`
   Release に添付する。

> **`build_apk` の実行条件**: 通常は `bumped==1 && exists==false`（新規リリース確定時のみ）。
> `workflow_dispatch` で既存の `patch-vX.Y.Z` タグを指定することでバックフィルも可能。
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
