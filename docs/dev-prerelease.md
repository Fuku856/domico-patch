# Dev プレリリース ワークフロー

`dev` ブランチのパッチを実機テストするためのプレリリースを手動で作成するワークフロー。
本番ワークフロー（`patch.yml`）とはタグ・リリース種別で完全に分離されており、
本番 Release への影響はない。

ワークフロー本体: [`.github/workflows/dev-prerelease.yml`](../.github/workflows/dev-prerelease.yml)

---

## 本番ワークフローとの違い

| 項目 | `patch.yml`（本番） | `dev-prerelease.yml` |
|------|---------------------|----------------------|
| トリガー | スケジュール（毎日）+ 手動 | **手動のみ** |
| 実行ブランチ | main | **dev** |
| タグ名 | `v${VN}-patch` | `patch-v${PV}-dev.${N}` |
| リリースタイトル | `Domico X.X.X Patched` | `[Preview] Patch vX.Y.Z / Base X.X.X` |
| リリース種別 | 通常リリース | **プレリリース** |
| パッチ版バンプ条件 | 自動（feat/fix 検知） | **bump あればビルド、なければスキップ**（`force=true` で強制） |
| 同バージョン再実行 | タグ存在時はスキップ | **常にビルド**（連番 N が進み別タグになる） |
| ノート自動生成 | `--generate-notes` | 同じ（`--generate-notes`） |

`actions/checkout` はトリガー元ブランチをそのまま使うため、
`dev` から実行すれば `dev` のパッチが自動的に適用される。

タグ名 `patch-v${PV}-dev.${N}` の `${PV}` は、その dev が収束する予定の本番リリース
`patch-v${PV}` と一致する。`${N}` は同じ `${PV}` に対する dev ビルドの連番で、
既存タグの最大 + 1。セムバーのプレリリース識別子なので
`0.6.0-dev.1 < 0.6.0-dev.2 < 0.6.0` の順序が保たれる。

本番リリースの番号は dev で何回ビルドしたかに左右されない（`patch-v*-dev.*` は
基準タグとして数えられないため）。詳細は [patch-versioning.md](patch-versioning.md)。

---

## 実行方法

### 前提

- CI セットアップ（Secrets 設定）が完了していること → [ci-setup.md](ci-setup.md)

### 手順

1. GitHub リポジトリの **Actions** タブを開く
2. 左サイドバーから **`Dev Pre-release (manual)`** を選択
3. **Run workflow** ボタンをクリック
4. ブランチが **`dev`** になっていることを確認
5. 必要に応じて **`force`** チェックボックスをオン（後述）
6. **Run workflow** を実行

### `force` オプション

`patch-v*` タグ以降に `feat:` / `fix:` / `perf:` などのコミットが無い場合、
ワークフローは Notice を出力してスキップする。

強制的にビルドしたい場合（例: CI 修正の確認、設定変更のみの場合）は
`force` チェックボックスをオンにして実行する。

### 完了後の確認

- Actions のログで全ステップが緑になったことを確認
- **Releases** ページに `patch-v${PV}-dev.${N}` タグのプレリリースが作成される
  - 例: `patch-v0.6.0-dev.2`
  - タイトル: `[Preview] Patch vX.Y.Z / Base X.X.X`
  - ラベル: **Pre-release**（GitHub UI でオレンジ色で表示）
  - 添付ファイル: `domico-patch-vX.Y.Z-dev.N-baseX.X.X.apks`

---

## インストール

本番リリースと同じ手順。`.apks` を SAI / Shizuku 等の分割対応インストーラで入れる。
詳細は [install.md](install.md)。

---

## 注意事項

- プレリリースは **実機テスト専用**。動作確認が取れたら `dev` → `main` へ PR して本番リリースすること。
- 再実行すると連番 N が進んで**別タグ**になるため、既存のプレリリースは削除されず履歴として残る。
- `--generate-notes` は前回リリース以降のコミット/PR を自動でまとめる。
- `force=false`（既定）かつ bump なしの場合、ビルドはスキップされ Notice のみ出力される。

---

## 関連

- 本番ワークフロー: [`.github/workflows/patch.yml`](../.github/workflows/patch.yml)
- パッチバージョン仕様: [patch-versioning.md](patch-versioning.md)
- CI セットアップ（Secrets 設定）: [ci-setup.md](ci-setup.md)
- インストール手順: [install.md](install.md)
