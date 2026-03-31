# 実装メモ

## 主要クラス
- `RecCheckClient`: クライアント初期化、HUD登録、キー割当
- `RecCheckClient`: クライアント初期化、HUD登録、キー割当
- `ObsConnectionManager`: OBS WebSocket 接続、状態更新、自動再接続、接続テスト
- `ObsConnectionSnapshot`: HUD がそのまま描画できる状態スナップショット
- `ModConfig` / `ModConfigManager`: 永続設定
- `ObsHudRenderer`: 右上 HUD 描画
- `ConfigScreen`: 設定画面
- `HelpScreen`: 接続手順と失敗例の案内

## 状態遷移
- `CONNECTING` -> 初回接続中 / 再接続中
- `CONNECTED_RECORDING` -> HUD 非表示
- `CONNECTED_NOT_RECORDING` -> 警告表示
- `DISCONNECTED` -> OBS未起動、ポート違い、接続失敗など
- `AUTH_FAILED` -> パスワード不一致
- `ERROR` -> 設定不備や通信プロトコル異常

## エラー処理方針
- 失敗は HUD に分かりやすい日本語で返す
- 例外はバックグラウンドスレッドで捕捉し、描画スレッドを止めない
- 接続失敗の再試行は自動化し、ログは増やしすぎない

## 今後の拡張ポイント
- 状態別のトースト通知
- OBS 側の録画開始ボタンをHUDに追加
- プロファイル別設定
- しきい値付きの警告音カスタム
