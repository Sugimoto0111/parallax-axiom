# Parallax Axiom / 視差公理

Minecraft Forge 1.20.1向けのエンドコンテンツModです。

わずかにずれて重なる世界を観測し、選んだ結果を現実として固定する――という世界観を、即死武器・不変化アーティファクト・屈折と視差を使った描画で表現します。

> `ultimatum` は初期開発時から使っている内部IDです。既存ワールドとの互換性を守るため、Mod IDとアイテムIDにはそのまま残しています。

## 必要環境

- Minecraft 1.20.1
- Forge 47.4.22以降
- Java 17
- Curios API（Forge 1.20.1）

## 実装済みアイテム

### 最終帰結 / Final Conclusion

```mcfunction
/give @s ultimatum:absolute_end
```

対象を左クリックすると、成立可能な死を探索してその帰結を確定する剣です。通常のMobには自然な死亡処理を優先し、死亡耐性が強い対象だけを段階的に強い処理へ移します。最後の手段として、通常の死亡そのものを拒否する対象を深層削除します。

- バニラおよび一般的なForge Mob
- Omni-Mobs 0.3.5.3
- The Trial Monolith
- Pig2

対象Modのjarはこのリポジトリに含まれません。

### 不変観測子 / Invariant Observer

```mcfunction
/give @s ultimatum:absolute_artifact
```

Curiosのartifactスロットへ装着し、プレイヤーを観測の基準点として不変化するアーティファクトです。

- ダメージ・死亡・強制削除への保護
- 段階式の飛行速度と慣性抑制
- 段差補助、暗視、広範囲アイテム吸引
- デバフ・放射線・拘束状態の解除
- ブロック／エンティティへの段階式リーチ延長
- プレイヤーの背後を追従する屈折構造体

各機能のキーはMinecraftのキー設定から変更できます。

## 観測と素材

オフハンドのバニラ望遠鏡は、最初に成立した観測経路を記録します。二つの経路を一つの望遠鏡へ混在させることはできません。

- エンダードラゴン、ウィザー、ウォーデンの帰結を合計50回観測すると、望遠鏡が「終像鏡」へ変化します。
- 望遠鏡をオフハンド、トーテムをメインハンドに持って不死のトーテムを50回発動すると、望遠鏡が「原像鏡」へ変化します。
- 「零位焦点」は二つの完成品に共通する予定の中核素材です。現在はアイテム登録のみで、レシピはまだありません。

```mcfunction
/give @s ultimatum:terminal_image_mirror
/give @s ultimatum:original_image_mirror
/give @s ultimatum:zero_focus
```

## 即死パイプライン

1. 通常ダメージによる自然死
2. 全 `LivingEntity` 共通のMixin死亡核
3. 既知Modの外部状態に対する専用死亡救済
4. ServerLevel、EntityTickList、セクション、追跡索引からの深層削除
5. 墓標UUIDによるEntity再登録の阻止

普通に倒せるMobでは死亡アニメーション、音、ドロップを保ちます。後段の処理は、それ以前の死亡経路を拒否した対象にだけ使用します。

## ビルドとテスト

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
```

ビルド成果物は `build/libs` に生成されます。現在のGameTestは、通常死亡、公開死亡APIを拒否するMob、`tickDeath` を停止するMob、墓標UUIDの再登録阻止、プレイヤー保護、望遠鏡による二系統の観測を検証します。

## 注意

このModはMixin、内部フィールド書き換え、保護コンテナの迂回、Entity索引の直接削除を使う実験的な実装です。大切なワールドへ導入する前にバックアップを作成してください。

ライセンスはAll Rights Reservedです。Minecraft ForgeおよびMinecraft Coder Packに関する告知は `LICENSE.txt` と `CREDITS.txt` を参照してください。
