# 単体試験仕様・結果書

文書版: 1.0 / 対象: CoreのDatabase、Ecoの入力検証

## 方針と結果

単体試験7件、すべてPASS。Databaseの4件は実SQLiteを用いたコンポーネント単位の試験であり、モックのみの孤立した試験ではない。Ecoの3件はDBアクセスを伴わない入力検証。CoreMod/EcoModのFabricイベント・コマンドアダプタは対象外。

| ID | 対象・条件 | 期待結果／確認結果 | 試験メソッド | 判定 |
|---|---|---|---|---|
| UT-C01 | 専用ワーカーと投入順序 | DBワーカーは呼出元と別スレッド。先行INSERT後のSELECT=7 | `UT_C01_workerThreadAndOrdering` | PASS |
| UT-C02 | commitとrollback | 成功更新=2。失敗更新9は取り消し、値2を維持 | `UT_C02_commitAndRollback` | PASS |
| UT-C03 | 接続の外部キー設定 | PRAGMA foreign_keys=1 | `UT_C03_foreignKeysEnabled` | PASS |
| UT-C04 | 停止処理 | 投入済み書込完了、停止後の追加要求は拒否 | `UT_C04_shutdownDrainsQueuedWrites` | PASS |
| UT-E01 | 0送金拒否 | DBに触れる前にIllegalArgumentException | `UT_E01_zero` | PASS |
| UT-E02 | 負数送金拒否 | DBに触れる前にIllegalArgumentException | `UT_E02_negative` | PASS |
| UT-E03 | 自己送金拒否 | DBに触れる前にIllegalArgumentException | `UT_E03_selfTransfer` | PASS |

## 実行環境・証跡

実施日: 2026-10-01（JST）。Java 21（Temurin、詳細はevidence/java-version.txt）、JUnit Platform Console Standalone 1.11.4、JUnit Jupiter、SQLite JDBC 3.46.1.0。テストごとにJUnitの一時ディレクトリへ実DBを生成する。性能負荷試験ではない。

今回のGradle再ビルドはLoomのUnix domain socket検出時に環境の操作制限で失敗した（evidence/gradle-build-attempt.log）。代わりに、実際のJavaソースをjavac --release 21でコンパイルし、JUnit Consoleから実行した。JUnit結果はGradle経由の結果を流用していない。21件実行、成功21、失敗0、skip0。

CoreModの変更箇所はjavacでコンパイル成功（evidence/core-adapter-compile.log）。Loomの再パッケージは未完了。

証跡: `evidence/test-run.log`、`evidence/junit/TEST-junit-jupiter.xml`。本版のCoreModアダプタ変更やMOD全体のパッケージ再生成、Minecraft内での動作はこのDB試験の合格対象ではない。前回の0.1.0ビルド成功は今回の変更後のビルド成功を意味しない。

## 再現

通常環境ではルートから `bash gradlew test` または `bash gradlew build`。Java 21が必要。Windowsはgradlew.batに置き換える。今回の直接実行方法は `scripts/run-db-tests.sh` を参照。このスクリプトにはJUnit standalone JAR、SQLite JDBC JAR、SLF4J API JAR、Java 21を指定する。依存JARの自動ダウンロードはしない。
