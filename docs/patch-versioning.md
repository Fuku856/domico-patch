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
v0.3.0 / base 1.5.4
```

「どの公式バージョンに対応した、パッチの何版か」が一目で分かる。

---

## 表示形式

[`version.py`](../scripts/version.py) が組み立てる文字列。チャンネルで2形態。

| チャンネル | 形式 | 例 | 用途 |
|-----------|------|----|------|
| `release` | `v{X.Y.Z} / base {app}` | `v0.3.0 / base 1.5.4` | 本番ビルド（`patch.yml`） |
| `dev` | `v{X.Y.Z}-dev+g{sha}[.dirty] / base {app}` | `v0.3.0-dev+g7afd66a2 / base 1.5.4` | dev プレリリース（`dev-prerelease.yml`） |

- `-dev+g{sha}`: dev ビルドに短縮コミット SHA を付け、追跡可能にする。
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

> **初期版**: タグが無い状態では全履歴を走査する。現在の履歴には破壊的変更が
> 無く `feat:` を含むため minor 算出で **`0.1.0`** になる。初回 main リリースで
> `release.yml` が `patch-v0.1.0` を自動作成し、以降はそのタグが基準になる。

---

## タグ体系

| タグ | 例 | 何を表すか | 誰が打つか |
|------|----|-----------|-----------|
| `patch-v{X.Y.Z}` | `patch-v0.3.0` | **パッチ版**の確定（CHANGELOG 境界） | `release.yml`（main push 時） |
| `v{versionName}-patch` | `v1.5.4-patch` | **ベース追従**ビルドの Release | `patch.yml`（公式更新検知時） |
| `v{versionName}-dev` | `v1.5.4-dev` | dev プレリリース | `dev-prerelease.yml` |

`version.py` と `cliff.toml` はバージョン境界として **`patch-v*` のみ**を見る
（ベース追従タグは無視する）。

---

## 仕組み（ビルド時の流れ）

```
version.py（番号算出）
   │  format_version(channel, app_version) → "v0.3.0 / base 1.5.4"
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
| [`dev-prerelease.yml`](../.github/workflows/dev-prerelease.yml) | `dev` | dev のパッチを手動でプレリリース |
| [`release.yml`](../.github/workflows/release.yml) | — | main push 時に版算出 → CHANGELOG → `patch-v*` タグ + Release |

`release.yml` の動作：

1. `version.py --number-only --print-bumped` で次版と増分有無を取得。
2. 増分が無い（feat/fix 等が無い）か、タグが既存ならスキップ。
3. git-cliff で `CHANGELOG.md` を生成。
4. `CHANGELOG.md` を `chore(release): … [skip ci]` でコミットし、`patch-v{X.Y.Z}`
   タグを作成して push。
5. GitHub Release を作成（CHANGELOG 当該節をノートに）。

> **重要**: いずれのワークフローも checkout は `fetch-depth: 0` + `fetch-tags`。
> 版算出に全履歴とタグが必要なため（shallow clone だと `0.0.0` になる）。

---

## 運用ガイド

### 版を上げる
**コミットメッセージを Conventional Commits で書くだけ**。版上げの手動操作は不要。

| やりたいこと | コミット例 | 結果 |
|--------------|-----------|------|
| バグ修正 | `fix: ロード遮断の誤判定を修正` | patch +1 |
| 機能追加 | `feat: 設定にダークモードを追加` | minor +1 |
| 互換性破壊 | `feat!: 設定キーを刷新` | major +1 |
| 文書のみ | `docs: README 更新` | 据え置き |

dev に積んだコミットが **main にマージされた時点**で `release.yml` が版を確定する
（dev→main マージ＝パッチリリース）。

### 番号を手動で固定したい
ビルド時に上書きできる：

```bash
python scripts/build.py … --patch-version "v1.0.0 / base 1.5.4"
```

### 確認用コマンド
```bash
python scripts/version.py                              # release 表示
python scripts/version.py --channel dev --app-version 1.5.4
python scripts/version.py --number-only --print-bumped # 次版 + 増分有無(1/0)
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
