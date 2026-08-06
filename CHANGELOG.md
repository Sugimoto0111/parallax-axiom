# Changelog

## 0.4.1

- 即死操作を左クリックだけに統一
- 墓標を同一UUIDだけに限定し、同種Mobの新規召喚を許可
- 常に `isRemoved() == false` を返すOmni-Mobsの完了判定をServerLevel索引基準へ修正

## 0.4.0

- 全非プレイヤー `LivingEntity` 向けの汎用Mixin死亡核を追加
- 同期HP、死亡フラグ、死亡時刻、攻撃者、死亡イベント、ドロップ、演出を直接成立
- `通常死 → 汎用死亡 → 専用救済 → 深層削除` の段階処理を実装
- Forgeイベントバスに依存しないMinecraftServer heartbeatを追加
- 墓標UUIDのEntity索引への再登録をMixinで阻止

## 0.3.6

- Omni-Mobsの保護HealthManagerを書き換え、可能な個体は通常の死亡処理へ移行
- 死亡完了時だけ深層削除を回避

## 0.3.5

- BossEventの直接非表示とクライアント削除パケットを追加

## 0.3.4

- Absolute Infinityの非Entityコントローラー検出と削除を追加

## 0.3.1

- Absolute End所持者の死亡・強制削除保護を追加
