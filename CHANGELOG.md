# Changelog

このパッチ(domico-patch)自身のリリースノート。Conventional Commits から自動生成。
ベースアプリ追従ビルド(`v{versionName}-patch`)とは別軸のバージョンです。
## [0.3.0] - 2026-07-01

### Bug Fixes

- Smali const/4→const/16 修正・dev-prerelease ワークフロー刷新
- Checkin-bypass がフレッシュ smali でチェック失敗する問題を修正
- パッチ設定クラッシュ・時間外チェックイン遷移しない を修正
- 時間外チェックインの受け取り画面が空表示になる問題を修正
- パッチ設定の内容を改善
- パッチに関するクレジットのテキストカラーを、ブラックに統一。
- 自動チェックインの親フラグ未参照とバイパスのconsume競合を修正

### CI

- Patch dry-run 失敗時に patch_check.log をアーティファクト保存

### Features

- パッチ設定画面を全画面化・モダンデザインに刷新
- 時間外チェックインの E1015 エラーをネットワーク層でバイパス
- 時間外チェックイン時に開始時間で自動送信する機能を追加（既定オフ）
- パッチ設定画面のカラーを、アプリのテーマカラーに統一

## [0.3.0-dev] - 2026-06-30

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

