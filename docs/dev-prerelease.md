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
| タグ名 | `v${VN}-patch` | `v${VN}-dev` |
| リリースタイトル | `Domico X.X.X Patched` | `[Preview] Domico X.X.X Patched` |
| リリース種別 | 通常リリース | **プレリリース** |
| 同バージョン再実行 | タグ存在時はスキップ | **常にビルド・上書き** |
| ノート自動生成 | `--generate-notes` | 同じ（`--generate-notes`） |

`actions/checkout` はトリガー元ブランチをそのまま使うため、
`dev` から実行すれば `dev` のパッチが自動的に適用される。

---

## 実行方法

### 前提

- CI セットアップ（Secrets 設定）が完了していること → [ci-setup.md](ci-setup.md)

### 手順

1. GitHub リポジトリの **Actions** タブを開く
2. 左サイドバーから **`Dev Pre-release (manual)`** を選択
3. **Run workflow** ボタンをクリック
4. ブランチが **`dev`** になっていることを確認して **Run workflow** を実行

### 完了後の確認

- Actions のログで全ステップが緑になったことを確認
- **Releases** ページに `v${VN}-dev` タグのプレリリースが作成される
  - タイトル: `[Preview] Domico X.X.X Patched`
  - ラベル: **Pre-release**（GitHub UI でオレンジ色で表示）
  - 添付ファイル: `domico-X.X.X-patch.apks`

---

## インストール

本番リリースと同じ手順。`domico-X.X.X-patch.apks` を SAI / Shizuku 等の分割対応インストーラで入れる。
詳細は [install.md](install.md)。

---

## 注意事項

- プレリリースは **実機テスト専用**。動作確認が取れたら `dev` → `main` へ PR して本番リリースすること。
- 同バージョンで再実行すると既存の `v${VN}-dev` リリースは**上書き**（削除→再作成）される。
- `--generate-notes` は前回リリース以降のコミット/PR を自動でまとめる。

---

## 関連

- 本番ワークフロー: [`.github/workflows/patch.yml`](../.github/workflows/patch.yml)
- CI セットアップ（Secrets 設定）: [ci-setup.md](ci-setup.md)
- インストール手順: [install.md](install.md)
