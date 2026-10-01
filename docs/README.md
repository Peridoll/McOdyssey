# McOdyssey v0.1 設計・試験納品資料

作成日: 2026-10-01 / 文書版: 1.0

## 納品物

1. [基本設計書](01-basic-design.md)
2. [詳細設計書](02-detailed-design.md)
3. [単体試験仕様・結果書](03-unit-test-report.md)
4. [結合試験仕様・結果書](04-integration-test-report.md)
5. [実機結合・受入試験手順書](05-server-acceptance-test.md)
6. [JUnit実行ログ](evidence/test-run.log)、[JUnit XML](evidence/junit/TEST-junit-jupiter.xml)、Java版・ビルド試行の証跡
7. リポジトリの単体・結合試験ソースと直接実行スクリプト

## 検証状態

| 区分 | 件数 | 今回の結果 |
|---|---:|---|
| 単体・コンポーネント試験 | 7 | 成功7 |
| Core・Eco・SQLite結合試験 | 10 | 成功10 |
| 既存DB回帰試験 | 4 | 成功4 |
| Minecraft実機結合・受入 | 16 | 未実施 |
| Gradle全体再ビルド | 1 | 実行環境のUnixソケット制限で未完了 |

テスト対象の実ソースはJava 21でコンパイルしJUnitから実行した。変更したCoreModも、既存のMinecraft/Fabric依存JARを使うjavacのコンパイル検証は成功した（evidence/core-adapter-compile.log）。これはLoomの再パッケージや実機起動の検証を代替しない。環境制限と回避方法は試験書に記載した。実機試験の成功や今回の変更後JARの完成を主張する資料ではない。

## 対象版・変更

基点コミット: `5381aae8b1828765ece6b1535c577c25f5ac4d98`。本資料と同じ納品コミットに、Residentsの抽出、単体7件と結合10件の追加、既存回帰4件の再実行、試験証跡を含む。生成JARは今回再ビルドが未完了のため、以前のJARを今回の変更後の成果物としては納品しない。取得したコミットIDはgit rev-parse HEADで確認できる。

## 要件と確認項目の対応

| 要件 | 主な証跡 | 実機項目 |
|---|---|---|
| FR01 住民登録 | IT02 | ST01、ST03、ST13 |
| FR02 初期金 | IT03 | ST03、ST12 |
| FR03 プロフィール | 表示は未確認 | ST05 |
| FR04 残高 | IT03、IT04 | ST05 |
| FR05 送金 | UT-E01〜03、IT04〜06、IT09、IT10 | ST06、ST08〜11 |
| FR06 原子性 | UT-C02、IT05、IT09 | ST08 |
| FR07 履歴 | IT04、IT08 | ST07 |
| FR08 永続化 | IT07 | ST12、ST14 |
| FR09 モジュール分離 | IT01 | ST01、ST02 |

## 利用方法

Markdown形式で納品し、GitHubとテキストエディタで閲覧・更新できる。文書の対象範囲を拡大する際は要件ID、試験ケース、実行証跡を同時に更新する。試験手順の追加だけでは合格扱いにしない。
