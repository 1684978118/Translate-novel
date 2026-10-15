# フロントエンド（Vue 3 + Vite）

日本語小説読解支援プラットフォームのフロントエンド。[backend](../backend) の REST API と連携します。

本アプリは、AI コーディングツールを活用して開発した個人開発プロジェクトです。

## 主な機能

- 本棚：TXT / EPUB のアップロード（章立て・段落の自動解析）、書籍の削除
- リーダー：段落単位の翻訳、ふりがな（ルビ）、文法解説、ワンタップ単語検索
- コレクション：ブックマーク、単語帳、読書進捗からの復帰

## 技術スタック

- Vue 3（`<script setup>`）+ Vue Router 4 + Pinia
- Element Plus
- axios：インターセプタで API 呼び出しに JWT を自動付与。トークンが切れた場合は refresh token で再取得します。

## 開発

```bash
npm install
npm run dev     # http://localhost:5173（/api を http://localhost:8080 へプロキシ）
npm run build   # dist/ へ出力
```

API はバックエンドに依存します。先に[リポジトリ直下の README](../README.md) を参照してバックエンドを起動してください。
