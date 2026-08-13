# HCE Tag

Android の HCE(Host Card Emulation)で **NFC Forum Type 4 Tag** を模擬し、
このスマホに他のスマホ(素の Android / iPhone)がタッチすると、**相手側で設定した URL が開く**ようにするアプリ。

## 仕組み

- HCE で NDEF Tag Application(AID `D2760000850101`)に応答し、Type 4 Tag として振る舞う
- NDEF メッセージには **URI レコード(https URL)1 件のみ** を格納する(Android/iOS 共通の単一設定)
  - Android 読み取り側: ブラウザで URL が開く。URL が **App Links** 検証済みアプリのものなら、そのアプリが直接開く
  - iPhone(XS 以降)読み取り側: バックグラウンドタグ読み取りの通知経由で Safari または **Universal Link** 対応アプリが開く
  - レコードを単一にすることで、読み取り側でのアプリ選択ダイアログを防ぐ
- 相手側のスマホには何もインストール不要

特定のアプリを開かせたい場合は、そのアプリの App Links / Universal Links 対応 URL(例: LINE の `https://line.me/…`)を設定する。

## 制約事項

- **HCE 側(このアプリの端末)**: 画面 ON であればロック中でも動作(`requireDeviceUnlock=false`)。Android 13 以降は画面 OFF でも動作しうる(`requireDeviceScreenOn=false`、ハードウェア依存)。端末設定の「NFC のロック解除必須(セキュア NFC)」が ON の場合は OS 側が優先される
- **リーダー側 Android**: タグディスパッチの仕様上、ロック解除状態が必要
- **HCE 側でのタグ読み取り抑止**: Android 同士をかざすと HCE 側もリーダーとして相手を読んでしまい、タグ読み取りアプリの選択ダイアログが出ることがある。本アプリの画面が前面にある間はこれを吸収して抑止する。ホーム画面等でタッチされる場合に出るときは、読み取り系アプリ(NFC Tools 等)を無効化するか、Android 14 以降の「NFC 設定 → タグアプリの設定」で制御する(ロック中はそもそもタグディスパッチが動かないため出ない)
- **iOS 読み取り側**: バックグラウンド読み取りは画面点灯時に動作(ロック中でも可、Apple Pay 使用中・カメラ起動中などは無効)
- **非対応端末(重要)**: NFC チップ内蔵の T4T NFCEE が有効な端末(例: Xperia 5 III / NXP チップ + `NXP_T4T_NFCEE_ENABLE=0x01`)では、NDEF 用 AID への通信をチップが横取りするため**本アプリは動作しない**(空のタグとして読まれる)。詳細は [Issue #5](https://github.com/yuna1107-k/HCE_Android/issues/5)。判定方法: `adb shell dumpsys nfc` で `AID_D2760000850101` の NFCEE_ID が `0x00` 以外なら非対応

## ビルド

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

- compileSdk 35 / minSdk 26 / Kotlin

## 使い方

1. アプリを起動し、開かせたい https URL を設定する(Android/iPhone 共通)
2. NFC を有効にして待機(画面 ON)
3. 相手のスマホの背面を重ねる → 相手側で URL(または App Links / Universal Links 対応アプリ)が開く

## デバッグ

- HCE 側のログ: `adb logcat -s Type4TagService`(SELECT / READ が届いているか確認できる)
- 読み取り側に「NFC Tools」等を入れると、タグとして NDEF 内容を直接確認できる
