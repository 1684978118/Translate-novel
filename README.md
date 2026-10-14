# Translate Novel — 日本語小説読解支援プラットフォーム

「日本語の小説を読む」と「日本語を学ぶ」をひとつの流れにまとめたフルスタックの Web アプリケーションです。
TXT / EPUB の小説をアップロードすると、章立ての自動解析と形態素解析を行い、読書画面では段落ごとの翻訳・ふりがな（ルビ）・文法解説・単語検索ができます。読書の進捗・ブックマーク・単語帳も自動的に保存されます。

## 主な機能

- ユーザー登録 / ログイン / トークンのリフレッシュ（Access Token + Refresh Token）
- TXT / EPUB のアップロードと解析（章の切り出し・文の分割はバックエンドで実施）
- 本棚：書籍の一覧・詳細・章一覧
- リーダー：段落単位の表示、スクロール位置からの進捗自動保存、前後章への移動
- 段落翻訳：LLM（OpenAI 互換 chat/completions API。Zhipu GLM での動作を確認済み）。キー未設定時は簡易翻訳へ自動フォールバック
- ふりがな（ルビ）表示：kuromoji による形態素解析に基づき生成
- 文法パターンの解説、ワンタップでの単語検索（LLM による辞書風の語義解説、フォールバック付き）
- ブックマークと単語帳（例文の登録も可能）

## 技術スタック

| レイヤー | 技術 |
| --- | --- |
| バックエンド | Java 8 / Spring Boot 2.7 / Spring Security + JWT / MyBatis-Plus / Flyway / MySQL 8 |
| フロントエンド | Vue 3（`<script setup>`）/ Vite / Pinia / Vue Router / Element Plus / axios |
| 日本語 NLP | kuromoji-ipadic（形態素解析・ルビ生成） |
| LLM | OpenAI 互換 API（Zhipu GLM／段落翻訳・語義の生成） |

## ディレクトリ構成

- `backend/`：Spring Boot サービス
- `frontend/`：Vue 3 アプリケーション
- `SPEC.md/`：初期の設計ドキュメント（実装はコードが正）

## 環境要件

- JDK：ソースレベルは Java 8。新しい JDK でもビルド可能（JDK 25 で動作確認済み）
  - Spring Boot 2.7 が依存解決する Lombok は新しめの JDK に非対応のため、`backend/pom.xml` で Lombok を `1.18.44` に固定しています
- Maven：`mvnw` ラッパー同梱のため個別インストール不要（初回実行時に Maven を自動ダウンロード）
- Node.js 18 以上（フロントエンドのビルド）
- MySQL 8（バックエンドはデフォルトでローカルの MySQL に接続）

## ローカル起動

### バックエンド

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

デフォルトでローカルの MySQL に接続し、`translate_novel` データベースへ業務データを保存します。起動時に DB・テーブルを自動作成します（スキーマは Flyway で管理。マイグレーションは `backend/src/main/resources/db/migration`）。

デフォルトの開発用設定：

```text
host: 127.0.0.1
port: 3306
database: translate_novel
username: root
password: 123456
```

環境変数で上書き可能：

```powershell
$env:MYSQL_HOST="127.0.0.1"
$env:MYSQL_PORT="3306"
$env:MYSQL_DATABASE="translate_novel"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="123456"
```

LLM 翻訳・語義生成を有効にする場合は API キーを設定：

```powershell
$env:OPENAI_API_KEY="your_api_key"
# OpenAI 互換のエンドポイントとモデル名も切り替え可能
$env:OPENAI_BASE_URL="https://open.bigmodel.cn/api/paas/v4"
$env:OPENAI_MODEL="glm-5.3-flash"
```

キー未設定の場合は簡易翻訳へ自動フォールバックし、他の機能には影響しません。

本番環境では JWT 署名鍵を必ず上書きしてください（デフォルト値はローカル開発専用）：

```powershell
$env:JWT_SECRET="32バイト以上のランダム文字列"
```

### フロントエンド

```powershell
cd frontend
npm install
npm run dev
```

開発時は Vite が `/api` を `http://localhost:8080` へプロキシします。

## 検証コマンド

バックエンドのテスト（H2 インメモリ DB を使用。ローカル MySQL 不要）：

```powershell
cd backend
./mvnw.cmd test
```

フロントエンドのビルド：

```powershell
cd frontend
npm run build
```

## 設計上の判断

- 自動テストは H2 インメモリ DB に固定し、ローカルの MySQL 状態に依存しないようにしています。
- スキーマは Flyway でバージョン管理しており、項目追加時は `V2` / `V3`... のマイグレーションを追記する方針です。
- refresh token はステートレスな JWT を採用（ローカルのモノリス開発向け）。マルチデバイスのログイン管理・強制ログアウト・セッション失効を実装する場合は、サーバー側の永続化とローテーション戦略の追加を推奨します。
