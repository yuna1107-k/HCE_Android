# HCE Tag

Android の HCE(Host Card Emulation)で **NFC Forum Type 4 Tag** を模擬し、
このスマホに他のスマホ(素の Android / iPhone)がタッチすると、**相手側で指定したアプリやリンクが開く**ようにするアプリ。

## 仕組み

- HCE で NDEF Tag Application(AID `D2760000850101`)に応答し、Type 4 Tag として振る舞う
- NDEF メッセージに次の 2 レコードを格納する
  1. **URI レコード(https URL)** — iPhone(XS 以降)のバックグラウンドタグ読み取り用。通知経由で Safari または Universal Link 対応アプリが開く
  2. **AAR(Android Application Record)** — Android 用。指定パッケージのアプリが起動(未インストールなら Play ストア)
- 相手側のスマホには何もインストール不要

## 制約事項

- **iOS**: 開けるのは https リンクのみ(Universal Link 対応アプリならそのアプリ、それ以外は Safari)。パッケージ指定での任意アプリ起動は不可。バックグラウンド読み取りは画面点灯時に動作(ロック中でも可、Apple Pay 使用中・カメラ起動中などは無効)
- **HCE 側(このアプリの端末)**: 画面 ON であればロック中でも動作(`requireDeviceUnlock=false`)。Android 13 以降は画面 OFF でも動作しうる(`requireDeviceScreenOn=false`、ハードウェア依存)。端末設定の「NFC のロック解除必須(セキュア NFC)」が ON の場合は OS 側が優先される
- **リーダー側 Android**: タグディスパッチの仕様上、ロック解除状態が必要

## ビルド

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

- compileSdk 35 / minSdk 26 / Kotlin

## 使い方

1. アプリを起動し、開かせたい対象を設定する
   - Android 相手: パッケージ名(端末のアプリ一覧から選択可)
   - iPhone 相手: https URL
2. NFC を有効にして待機(画面 ON)
3. 相手のスマホの背面を重ねる → 相手側でアプリ/リンクが開く
