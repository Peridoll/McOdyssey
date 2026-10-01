# McOdyssey v0.1 詳細設計書

文書版: 1.0 / 作成日: 2026-10-01

## 1. クラスと責務

| モジュール | クラス | 責務 |
|---|---|---|
| Core | CoreMod | Fabric起動・ログイン・停止イベントの接続、DBの公開 |
| Core | Database | JDBC接続、単一ワーカー、Future、トランザクション、終了待機 |
| Core | Residents | 住民スキーマ初期化とUUIDによる登録・名前更新 |
| Eco | EcoMod | Eco初期化、ログイン時口座生成、Brigadierコマンド、応答 |
| Eco | Economy | 口座の遅延生成、残高、原子的送金、履歴照会 |

`Residents` は今回、CoreModにあった既存のSQLを分離したクラス。ゲームイベントを起動しなくても、結合試験から実際の住民登録処理を呼べるようにした。登録の仕様とSQLは維持している。

## 2. ライフサイクル

1. Coreの `SERVER_STARTING` でワールドの保存先からDatabaseを生成。
2. Residents.initializeを専用ワーカーへ投入し、完了をjoinで待機。
3. Ecoの `SERVER_STARTED` でEconomyを生成、Ecoのテーブルを初期化。
4. JOIN時、Coreは住民を登録。Ecoも住民登録完了を待って口座を生成する。登録はUPSERTなので二重呼出でも住民数は増えない。
5. コマンド受付後はDBワーカーへ処理を依頼し、完了時に `server.execute` でMinecraft側のスレッドへ応答を戻す。
6. `SERVER_STOPPED` でDBワーカーの終了を待機し、Coreの静的参照を解除する。

このイベント接続と実際のコマンド受付はDB結合試験の範囲外であり、実機試験の対象とする。

## 3. DB定義

### residents

| 列 | SQL型 | 制約 | 内容 |
|---|---|---|---|
| uuid | TEXT | PRIMARY KEY | UUIDの文字列表現 |
| name | TEXT | NOT NULL | 最後に登録されたプレイヤー名 |
| first_join | INTEGER | NOT NULL | 初回登録時刻、Unix epochミリ秒 |
| last_join | INTEGER | NOT NULL | 最終登録時刻、Unix epochミリ秒 |

### eco_accounts

| 列 | SQL型 | 制約 | 内容 |
|---|---|---|---|
| uuid | TEXT | PRIMARY KEY、REFERENCES residents(uuid) | 住民と1対1の口座 |
| balance | INTEGER | NOT NULL、CHECK(balance>=0) | 整数MC |

### eco_transactions

| 列 | SQL型 | 制約 | 内容 |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | 取引順序のID |
| sender | TEXT | NOT NULL、REFERENCES eco_accounts(uuid) | 送金元 |
| recipient | TEXT | NOT NULL、REFERENCES eco_accounts(uuid) | 受取先 |
| amount | INTEGER | NOT NULL、CHECK(amount>0) | 金額 |
| created_at | INTEGER | NOT NULL | 保存時刻、Unix epochミリ秒 |

すべて `CREATE TABLE IF NOT EXISTS`。スキーマ版管理や既存列の自動変更はない。追加索引、ON DELETE CASCADEはない。履歴検索は件数が増えた場合の性能評価が必要。

## 4. 共通DB処理

Databaseの生成で親ディレクトリを作成し、SQLite JDBCをロードする。submitはConnectionを処理ごとに開き、`foreign_keys=ON`、`busy_timeout=5000` を設定する。処理終了時にConnectionとStatementを閉じる。住民初期化時にjournal_modeをWALへ設定する。

transactionはautoCommitをfalseにし、成功時commit、例外時rollback。例外はCompletionExceptionでFutureへ伝える。単一ワーカーであるため、同じDatabaseインスタンス内の処理は受付順に実行される。他プロセス・複数サーバー間の分散排他ではない。

## 5. 住民登録・残高

住民登録SQLは `INSERT ... ON CONFLICT(uuid) DO UPDATE`。競合時はnameとlast_joinのみ更新し、first_joinは維持する。

口座生成は `INSERT OR IGNORE ... balance=1000`。残高照会もtransaction内で口座を確認してからSELECTする。住民が未登録なら外部キー制約で失敗する。口座が未生成の既存住民へ初めて送金する場合も、その口座の初期所持金が生成される。

## 6. 送金アルゴリズム

1. amount<=0または送受金UUIDが同一ならDBにアクセスせず失敗Futureを返す。
2. transactionを開始し、送金元・受取先の口座をensureする。
3. `UPDATE eco_accounts SET balance=balance-? WHERE uuid=? AND balance>=?`。更新件数が1でなければ残高不足として失敗。
4. `UPDATE eco_accounts SET balance=balance+? WHERE uuid=? AND balance<=?`。最後の引数はLong.MAX_VALUE-amount。更新件数が1でなければ受取上限超過として失敗。
5. eco_transactionsへ送金元・相手・金額・時刻をINSERT。
6. commit。途中の例外では口座生成を含めてrollbackする。

口座が既に存在するとき、送金前後の2口座の残高合計は同一である。コマンドの成功表示はDBのcommit完了後に送る。

## 7. 履歴・応答

履歴はsenderまたはrecipientが自分の行を抽出する。CASE式で相手UUIDを決めてresidentsとJOINし、id降順で最大10件を返す。文字列形式は `#ID 送金 → 相手 : 金額 MC` または `#ID 受取 ← 相手 : 金額 MC`。時刻は保存するが現在の表示には含めない。

EcoModは実行者をgetPlayerOrThrowで取得。payの相手はEntityArgumentType.playerで単一のオンラインプレイヤーとして解決し、金額はLongArgumentType.longArg(1)で解析する。

例外原因をたどりIllegalArgumentExceptionならメッセージを表示する。その他は詳細をSLF4Jログに記録し、一般的なDBエラー表示にする。応答表示前に切断した場合の扱いは実機試験で確認する。

## 8. ビルド・互換性

Gradle 8.8、Loom 1.7.4、Yarn 1.21.1+build.3、Java release 21。CoreとEcoはそれぞれremapJarを生成。EcoのコンパイルはCoreのnamedElementsを参照する。fabric.mod.jsonでEco → mcodyssey_core >=0.1.0を宣言する。

GitHub ActionsはJava 21でビルドし、Core/Ecoの導入JARをアップロードする。試験はJUnit Jupiter。自動試験と実機試験の一覧・実施結果は別冊参照。
