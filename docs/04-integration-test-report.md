# 結合試験仕様・結果書

文書版: 1.0 / 対象: Residents → Economy → Database → SQLite

## 方針と結果

Coreの本番Residents処理とEcoの本番Economyを実SQLiteへ接続する結合試験10件、すべてPASS。従来のDB回帰試験4件も今回再実行し、すべてPASS。ゲーム内のイベント接続を再現する試験ではない。

| ID | 対象・条件 | 期待結果／確認結果 | 試験メソッド | 判定 |
|---|---|---|---|---|
| IT01 | Core単独 | 住民を登録してもecoテーブル数0 | `IT01_coreAloneHasNoEconomyTables` | PASS |
| IT02 | 住民の再登録と名前変更 | 住民1行、名前更新、first_join維持、last_join>=first_join | `IT02_registerRenameWithoutResettingFirstJoin` | PASS |
| IT03 | 再初期化・再登録 | 100送金後の残高900/1100を維持 | `IT03_repeatedInitializationAndRegistrationDoNotIssueMoneyAgain` | PASS |
| IT04 | 送金と双方の履歴 | 250送金で750/1250、相手名と送受方向を確認 | `IT04_successfulTransferRecordsBothHistoryDirections` | PASS |
| IT05 | 残高不足 | 1001送金は拒否、1000/1000と履歴なしを維持 | `IT05_insufficientFundsRollBackAllChanges` | PASS |
| IT06 | 20件同時投入 | 100ずつの送金は10件成功、0/2000、履歴10件 | `IT06_concurrentTransfersHaveExactlyTenSuccesses` | PASS |
| IT07 | DB再接続 | 閉じて再生成後も750/1250、受取履歴1件 | `IT07_reopenDatabasePreservesBalancesAndLedger` | PASS |
| IT08 | 履歴の上限・順序・名前 | 12件中ID12〜3の10件、変更後の相手名 | `IT08_historyIsNewestFirstLimitedToTenAndUsesCurrentName` | PASS |
| IT09 | 受取上限超過 | 受取Long.MAX_VALUEで1送金を拒否、元1000、履歴なし | `IT09_overflowRollsBackDebitAndLedger` | PASS |
| IT10 | 未登録住民 | 外部キーで拒否、元1000、履歴なし | `IT10_unknownResidentIsRejectedByForeignKey` | PASS |

## 既存回帰試験

| ID | 対象・条件 | 期待結果／確認結果 | 試験メソッド | 判定 |
|---|---|---|---|---|
| REG01 | 送金・再接続 | 750/1250、1件の履歴、再接続後750 | `transferAndPersistence` | PASS |
| REG02 | 残高不足・自己・0 | 失敗、元1000、履歴なし | `insufficientAndInvalidPaymentsDoNotChangeBalance` | PASS |
| REG03 | 同時送金 | 20件投入後0/2000 | `concurrentTransfersConserveMoney` | PASS |
| REG04 | 受取上限 | 失敗、元1000、履歴なし | `recipientOverflowRollsBackDebit` | PASS |

## 実行環境・証跡

実施日: 2026-10-01（JST）。Java 21（Temurin、詳細はevidence/java-version.txt）、JUnit Platform Console Standalone 1.11.4、JUnit Jupiter、SQLite JDBC 3.46.1.0。テストごとにJUnitの一時ディレクトリへ実DBを生成する。性能負荷試験ではない。

今回のGradle再ビルドはLoomのUnix domain socket検出時に環境の操作制限で失敗した（evidence/gradle-build-attempt.log）。代わりに、実際のJavaソースをjavac --release 21でコンパイルし、JUnit Consoleから実行した。JUnit結果はGradle経由の結果を流用していない。21件実行、成功21、失敗0、skip0。

CoreModの変更箇所はjavacでコンパイル成功（evidence/core-adapter-compile.log）。Loomの再パッケージは未完了。

証跡: `evidence/test-run.log`、`evidence/junit/TEST-junit-jupiter.xml`。本版のCoreModアダプタ変更やMOD全体のパッケージ再生成、Minecraft内での動作はこのDB試験の合格対象ではない。前回の0.1.0ビルド成功は今回の変更後のビルド成功を意味しない。

## 再現

通常環境ではルートから `bash gradlew test` または `bash gradlew build`。Java 21が必要。Windowsはgradlew.batに置き換える。今回の直接実行方法は `scripts/run-db-tests.sh` を参照。このスクリプトにはJUnit standalone JAR、SQLite JDBC JAR、SLF4J API JAR、Java 21を指定する。依存JARの自動ダウンロードはしない。

## 残課題

実機の結合・受入試験16件は未実施。`05-server-acceptance-test.md` に手順と期待結果を記載した。現在の範囲では、SQLite保存・取引処理の連携は確認済み、Minecraftへの導入・操作の受入は未完了である。
