# 差分メモ（パッチ群）

すべて `scripts/patch_smali.py` がアンカー基準・冪等で適用し、対象クラスは全て classes4。
新規ヘルパは `scripts/patch_assets/vn/com/bravesoft/androidapp/patch/*.smali` を classes4 へ配置する。
各パッチは `PatchPrefs` のフラグ（既定 true）でゲートされ、設定画面から個別に切り替えられる。

## 1. ログイントースト クリックスルー
- ファイル: `vn/com/bravesoft/androidapp/utils/AlertUtils.smali`
- メソッド: `displayToastContract(Landroid/app/Activity;Ljava/lang/String;)V`

`Landroid/view/Window;->clearFlags(I)V` をアンカーにし、その直後へ `toastEnabled` ゲート付きで
`FLAG_NOT_FOCUSABLE(0x8) | FLAG_NOT_TOUCHABLE(0x10) = 0x18` を `addFlags` する。

```smali
    invoke-virtual {v1, v3}, Landroid/view/Window;->clearFlags(I)V
    # domico-patch: gated login-toast click-through
    sget-boolean v3, Lvn/com/bravesoft/androidapp/patch/PatchPrefs;->toastEnabled:Z
    if-eqz v3, :domico_toast_skip
    const/16 v3, 0x18
    invoke-virtual {v1, v3}, Landroid/view/Window;->addFlags(I)V
    :domico_toast_skip
```

- `v1` = Dialog の `Window`、`v3` = `clearFlags` 直後で不要になる一時レジスタの再利用。
- 表示と2秒後の自動 dismiss はそのまま。

## 2. ロード表示クリックスルー（選択的）
- `views/FrameLayoutLoading.smali`: 内側全画面 `relativeLayout` の `setClickable(Z)` を
  `loadingEnabled` に応じて切替（有効時はクリックスルー＝非クリッカブル）。
- `di/AppModule.smali` `provideRetrofit`: OkHttp ビルダの `build()` 直前に
  `PatchTrafficInterceptor.add(builder)` を挿入（スクラッチ register 不要の静的ヘルパ）。
- インターセプタは非GET（POST/PUT/DELETE）通信の前後で `PatchLoadingState.enter()/exit()` を呼び、
  進行中はメインスレッドで現在 Activity に透明なクリック吸収オーバーレイを乗せて二重送信を防ぐ。
- GET は素通りなので取得・遷移ロードは完全に操作可能。

## 3. テレメトリ停止 + Ad-ID 遮断
- `MyApplication.smali` `onCreate`: `invoke-super onCreate` 直後に
  `PatchInit.onAppCreate(this)` を 1 命令注入。
- `PatchInit` が prefs ロード → `PatchTelemetry.apply()`（`telemetryOff` 時に Analytics/Crashlytics/
  Performance 停止＋広告同意 DENIED、全体 try/catch で起動を壊さない）→ Activity トラッカ登録。

## 4. 設定画面の導線
- `ui/MenuFragment.smali` `init(View)`: 末尾の `return-void` 直前に
  `PatchSettingsEntry.install(binding)` を注入（`binding` は MenuFragment のフィールドから取得）。
- `PatchSettingsEntry` が `containerTop` に「パッチ設定」行を追加（タグで冪等）し、
  `versionContain` に長押しリスナを付与。どちらも `PatchSettingsOpener` → `PatchSettingsDialog.show()`。

## 5. 時間外チェックイン（裏機能・既定オフ）
- 対象: `ui/HomeFragment.smali`（`smali_classes4`）、新規ヘルパ `patch/PatchCheckIn` /
  `patch/PatchCheckInConfirm`。フラグ `PatchPrefs.checkinEnabled`（キー `checkin_outoftime`、**既定 false**）。
- 注入A（ボタン再有効化）: `showUICheckIn` 内、`isCheckInTime()` 結果を渡す
  `stateButton(Landroid/view/View;Z)V` 呼び出しの直後に
  `PatchCheckIn.enableOutOfTime(btnView, dto, isCheckInTime)` を挿入。
  ON かつ時間外（`isCheckInTime==false`）かつ未チェックイン（`getCheckIn()==0`）のときだけ
  `setEnabled(true)`（alpha 0.5 のグレー見た目は維持）。アンカーは `->isCheckInTime()Z`
  （HomeFragment 内で一意）→ 直後の `move-result`（bool reg）→ その reg を使う `stateButton`。

```smali
    invoke-direct {p0, v1, v3}, Lvn/com/bravesoft/androidapp/ui/HomeFragment;->stateButton(Landroid/view/View;Z)V
    # domico-patch: allow out-of-time check-in (gated)
    invoke-static {v1, p1, v3}, Lvn/com/bravesoft/androidapp/patch/PatchCheckIn;->enableOutOfTime(Landroid/view/View;Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;Z)V
```

- 注入B（確認ゲート）: `checkInAction(MenuForDayDTO)` の `.locals` 直後に挿入。
  `PatchCheckIn.confirmOutOfTime(this, dto)` が true（＝時間外・未チェックインで確認ダイアログ表示）
  なら `return-void` で公式処理を中断。false なら公式どおり続行。

```smali
    # domico-patch: out-of-time check-in confirm gate
    invoke-static {p0, p1}, Lvn/com/bravesoft/androidapp/patch/PatchCheckIn;->confirmOutOfTime(Landroidx/fragment/app/Fragment;Lvn/com/bravesoft/androidapp/model/MenuForDayDTO;)Z
    move-result v0
    if-eqz v0, :domico_checkin_continue
    return-void
    :domico_checkin_continue
```

- 確認ダイアログは純正 `AlertUtils.showAlertDialogCancel(ctx, true, msg, null, null, title, cb)` を再利用
  （OK→`CallbackAlertDialog.actionDoneClick()`、キャンセル→dismiss のみ）。`cb` は
  `PatchCheckInConfirm` で、OK 時に `CheckInDialog.Companion.newInstance(dto).show(childFM,"CheckInDialog")`。
  食事名は `isBreakfast()`＋`isJapanFoodReserved()`/`isWesternFoodReserved()`（=公式 `CheckInDialog` と同じ判定）から
  和食/洋食/朝食/夕食 を出す。和食/洋食の正しい表示と実チェックイン（`HomeModelView.checkIn(reservationId)`）は
  公式 `CheckInDialog` がそのまま担う。
- 設定: `PatchPrefs` に `checkinEnabled` フィールド追加、`load()` で `checkin_outoftime` を既定 false で読む。
  Switch 初期値が既定オフを反映するよう `PatchPrefs.get` を 3 引数化（既定値受け取り）し、
  `PatchPrefs.defaultOf(key)`（`checkin_outoftime` のみ false）を `PatchSettingsDialog.addRow` から使う。
- サーバー影響: 送信は公式と同一（`POST v1/reservations/checkin`、ボディ `reservation_id` のみ）。
  `is_checkin_time` はサーバー提供フラグで、サーバーが checkin でも時間窓を強制していれば時間外要求は
  エラーで弾かれる（無害・無効）。成功する場合は時間外チェックインがサーバー記録に残る点に留意。
- ネットワーク差し替え（`patch/PatchCheckInBypass`, OkHttp Interceptor・`checkinEnabled` ゲート）:
  - 送信: `POST */checkin` が 4xx かつボディに `E1015` を含む場合のみ HTTP 200 + 空 `BaseResponse`
    （`{"code":"","message":""}`）に差し替え、アプリを成功扱いにして `CheckInDialog` の
    `callbackCheckIn`→`PatchCheckInCallback.onCheckInSuccess()` を発火させる。
  - 受け取り情報: `CheckInCompletedDialog` は表示時に自前で `GET v1/reservations/{id}/checkin` を叩く。
    時間外は当該予約が「すでにキャンセル」扱いでこの GET も 4xx になり、本来サーバーが返す
    アバター/氏名/部屋番号/食事種別が来ず画面が空になる。そこで `PatchCheckInCallback` がダイアログ
    表示直前に `PatchCheckInInfo.prepare(homeFragment, dto)` を呼び、端末内の `UserCtrl.getUser()`
    （アバター/氏名/部屋番号）と `MenuForDayDTO`（朝食/夕食・和食/洋食）から `CheckInInformationResponse`
    相当の JSON を合成・保持する。`PatchCheckInBypass` が上記 GET の 4xx を受けたとき
    `PatchCheckInInfo.consume()` で取り出し、HTTP 200 + その JSON に差し替えて受け取り画面を再現する。
  - 食事種別マッピング（`CheckInCompletedDialog.setUpData` の判定に一致）:
    `type_of_meal==1`→朝食（`type_of_food==1` 和食 / それ以外 洋食）、`type_of_meal!=1`→夕食。
    `MenuForDayDTO.isBreakfast()`→朝食=1/夕食=2、`isJapanFoodReserved()`→和食=1/洋食=2。
  - 既知の制限: 合成 `date` は端末現在日付（`yyyy-MM-dd`）。日付跨ぎ（深夜の前日夕食チェックイン等）では
    表示日がずれ得る。受け取り画面はすべて端末側合成でサーバーにチェックイン記録は残らない。

## ビルド方式: dex 差し替え（apktool 全体リビルドは使わない）
apktool で `resources.arsc` / `AndroidManifest.xml` を再エンコードすると、一部端末（Xiaomi/HyperOS 等）が `INSTALL_FAILED_USER_RESTRICTED: Invalid apk` で弾く。そこで **外科的 dex 差し替え** に変更した。

- [`scripts/patch_apk.py`](../scripts/patch_apk.py) が `AlertUtils` を含む `classes4.dex` のみを baksmali → patch → smali で作り直し、他エントリ（resources / manifest / 他 dex）はそのまま再パッケージする。
- 検証: 元 base と比べ `classes4.dex` だけが変化し、他エントリの内容は一致。Xiaomi POCO F7 Pro（HyperOS）で `adb install-multiple` 成功。

## 自動適用（公式更新への追従）
smali 編集は [`scripts/patch_smali.py`](../scripts/patch_smali.py) が行う。版ズレに強くするため:

- 行番号ではなく **クラス + メソッド + 命令パターン** をアンカーにする。
- `clearFlags(I)V` 呼び出しからウィンドウ用・一時レジスタを動的に取得して挿入（レジスタ名・個数の変化に耐性）。
- **冪等**: 既にマーカーがあればスキップ。
- アンカーが見つからなければ非0終了し、CI が版変更を検知して失敗する（要手動確認）。

## 言語（日本語）
日本語文言は base になく `config.ja` スプリット側にある。ローカルは端末から pull、CI は Google Play（`locale=ja_JP`）で取得して同梱する。詳細は [install.md](install.md)。

## トースト: 非表示化（不採用）
通知の表示自体を消す案（`displayToastContractInfo()` を早期 return、または `displayToastContract` 先頭で `return-void`）もあるが、通知は残したいというユーザ方針によりクリックスルー化を採用。

## 関連
- 調査の詳細: [findings.md](findings.md)
- ビルド・署名・配布手順: [install.md](install.md)
