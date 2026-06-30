# Changelog

このパッチ(domico-patch)自身のリリースノート。Conventional Commits から自動生成。
ベースアプリ追従ビルド(`v{versionName}-patch`)とは別軸のバージョンです。
## [0.2.0] - 2026-06-30

### CI

- Release.yml に手動タグ指定のAPKバックフィル経路を追加

### Documentation

- ワークフロー・自動パッチ関連ドキュメントを更新

### Features

- 裏機能「時間外チェックイン」（既定オフ） を追加

## [0.1.0] - 2026-06-30

### Bug Fixes

- Patch_apk.py
- ロード遮断とメニュー設定行が機能しない不具合を修正
- ロード遮断をHTTPメソッドからURLパス判定へ変更
- 変更された dex shard のみ再アセンブルし未変更 dex はバイト維持
- パッチ版文字列を smali リテラルとして安全にエスケープ

### CI

- Dev ブランチ動作確認用プレリリースワークフロー追加
- Dev ブランチ動作確認用プレリリースワークフロー追加
- パッチ版 bump 時に公式版へパッチ適用した APK を patch-v に添付

### Documentation

- 表記統一・重複削除で整理
- Add CI setup guide (apkeep AAS token + GitHub Secrets)
- Dev-prerelease ワークフローの実行ブランチを dev に修正
- パッチバージョン仕様のドキュメントを追加
- パッチ版のベース版表記を base v{x} に変更
- Dev プレリリースワークフローの仕様・実行手順を追加
- Dev-prerelease の比較表「実行ブランチ」列の誤記を修正

### Features

- APKバージョン確認の改善とCIフロー最適化 (#15)
- パッチ群追加（プライバシー保護・ロード表示・設定画面） (#16)
- 設定ダイアログUI調整と下部ナビ長押し導線を追加
- 公式更新ごとに公式APKミラーRelease(v{vn}-official)を自動作成
- パッチ版を Conventional Commits から自動算出して設定画面に表示
- 設定画面のクレジットを中央揃え・著作権表記付きに改善
- Base APK probe + patch dry-run (lightweight version check)

### Other

- Main を dev に取り込み (PR #23 のコンフリクト解消)

