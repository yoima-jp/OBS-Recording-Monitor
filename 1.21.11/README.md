# OBS Recording Monitor

OBS Studio の録画状態を監視し、録画していない時だけ Minecraft HUD に警告を出す Fabric クライアント mod です。

## できること
- OBS WebSocket 経由で録画状態を監視
- 録画中は非表示、未録画時のみ警告表示
- 接続失敗、認証失敗、OBS未起動を別表示
- 設定画面内に接続ガイドと接続テストを内蔵

## 使い方
1. OBS Studio を起動
2. OBS の「ツール」→「obs-websocket設定」を開く
3. WebSocket を有効化し、必要ならパスワードを設定
4. Minecraft で Mod Menu から `OBS Recording Monitor Settings` を開く
5. ホスト、ポート、パスワードを入れて `接続テスト`
6. 成功したら保存

## ビルド
```bash
./gradlew build
```

## 設定ファイル
`config/reccheck.json`
