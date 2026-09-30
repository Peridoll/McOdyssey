# McOdyssey

Minecraftの機能を個別に導入できるFabricサーバーMOD群。初期対象は **Minecraft 1.21.1 / Java 21 / Fabric Loader 0.16.14以上**。

| フォルダ | MOD | 役割 | 必須依存 |
|---|---|---|---|
| `core` | McOdyssey-Core | 住民UUID・ログイン記録・非同期DB処理 | Fabric API |
| `eco` | McOdyssey-Eco | 財布・送金・取引履歴 | Core、Fabric API |
| `simulator` | 経済シミュレーター | 既存の独立した実験用プログラム | MODとは未接続 |

CoreはEcoを参照しません。将来のTechはCoreだけに依存させることで、Core + Techのサーバーに経済を導入する必要がなくなります。Tech自体はまだ実装していません。元の空のPaper雛形を置き換えています。

## ビルド

Java 21を用意して、Linux/macOSでは `bash gradlew build`、Windowsでは `gradlew.bat build`。

生成物は `core/build/libs/McOdyssey-Core-0.1.0.jar` と `eco/build/libs/McOdyssey-Eco-0.1.0.jar`。`-sources.jar` は導入しません。

## 導入

1. Minecraft 1.21.1のFabricサーバーを用意。
2. Fabric API（1.21.1版）、Core、Ecoの3つのJARを `mods` に配置。
3. サーバーを起動し、プレイヤーでログイン。

サーバー側だけに導入するMODです。新規住民に1,000 MCを付与し、再ログインでは付与しません。

- `/mco profile` — 住民名と残高
- `/mco balance` — 残高
- `/mco pay <オンラインプレイヤー> <整数額>` — 送金
- `/mco history` — 自分の直近10件の送受金履歴

金額は整数。自分への送金・0以下・残高不足・残高上限超過は拒否します。管理者による発行、店、価格、税、工場、独自GUIは次段階です。

## 保存とバックアップ

ワールドフォルダ内の `mcodyssey/core.sqlite` に住民情報とEcoのテーブルを保存します。ワールド単位の通貨です。DBの読み書きは単一の専用ワーカーで順序付け、送金の引落・入金・履歴は一つのトランザクションで保存します。DBエラー時にメモリ上の代替通貨は作りません。

バックアップはサーバー停止後にワールドをコピーしてください。起動中のSQLiteはWALを使うため、DB本体だけのコピーでは最新の取引が欠ける場合があります。

現在はSQLite専用。MySQL/PostgreSQLや外部サービスとの同期は未実装です。共通DB基盤の上にモジュール別テーブルを置き、Ecoを入れなければ財布のテーブルも作りません。Coreの住民登録はUUIDを主キーとして名前の変更に対応します。

## 検証

`bash gradlew build` でビルドとEcoのDBテストを実行。送金と永続化、不正入力・残高不足、同時送金の通貨総量、受取上限超過時のロールバックを検証します。実ゲームの導入確認は別途サーバーで行ってください。
