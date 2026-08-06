# Ultimatum

Minecraft Forge 1.20.1向けの、段階的な汎用即死処理と所持者保護を実装するModです。

## 必要環境

- Minecraft 1.20.1
- Forge 47.4.22以降
- Java 17

## 使用方法

```mcfunction
/give @s ultimatum:absolute_end
```

`Absolute End` を持って対象を左クリックすると実行処理を開始します。右クリックには機能を割り当てていません。剣をメインハンドまたはオフハンドに持っているプレイヤーには、通常ダメージ・死亡・一部の強制削除に対する保護が適用されます。

## 即死パイプライン

1. 通常ダメージによる自然死
2. 全 `LivingEntity` 共通のMixin死亡核
3. 既知Modの外部状態に対する専用死亡救済
4. ServerLevel、EntityTickList、セクション、追跡索引からの深層削除
5. 墓標UUIDのEntity索引への再登録阻止

通常のMobは最初の自然死経路を使います。強い死亡耐性を持つMobだけが後段へ進みます。Pig2は通常死亡の試行自体が危険なため、隔離された専用経路を使います。

## 対応状況

- バニラおよび一般的なForge Mob
- Omni-Mobs 0.3.5.3
- The Trial Monolith
- Pig2

対象Modのjarはこのリポジトリに含まれません。

## ビルドとテスト

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
```

ビルド成果物は `build/libs` に生成されます。現在のGameTestは、通常死亡、公開死亡APIを拒否するMob、`tickDeath` を停止するMob、墓標UUIDの再登録阻止、プレイヤー保護を検証します。

## 注意

このModはMixin、内部フィールド書き換え、保護コンテナの迂回、Entity索引の直接削除を使用する実験的な実装です。テスト用ワールドで使用してください。

ライセンスは `mods.toml` の宣言どおり All Rights Reserved です。Minecraft ForgeおよびMinecraft Coder Packに関する告知は `LICENSE.txt` と `CREDITS.txt` を参照してください。
