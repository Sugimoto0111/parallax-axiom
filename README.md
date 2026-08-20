# Parallax Axiom

Minecraft Forge 1.20.1向けのエンドコンテンツModです。段階式の強制死亡処理、Curios装備によるプレイヤー保護と移動補助、独自シェーダーを使ったアイテム／装備描画を実装しています。

## 必要環境

| 項目 | バージョン |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.22以降 |
| Java | 17 |
| Curios API | Forge 1.20.1対応版 |

## 登録ID

| 種別 | ID |
| --- | --- |
| Mod | `parallax_axiom` |
| Java package | `dev.srryo.parallaxaxiom` |
| 最終帰結 | `parallax_axiom:final_conclusion` |
| 不変観測子 | `parallax_axiom:invariant_observer` |
| 零位焦点 | `parallax_axiom:zero_focus` |
| 原像鏡 | `parallax_axiom:original_image_mirror` |
| 終像鏡 | `parallax_axiom:terminal_image_mirror` |

## 最終帰結

左クリックした対象へ、サーバー側で段階式の強制死亡処理を実行します。通常のMobでは死亡アニメーション、死亡音、ドロップを維持し、対象が前段の処理を拒否した場合だけ次の段階へ進みます。

1. 通常ダメージと通常死亡
2. 全 `LivingEntity` 共通のMixin死亡核
3. 外部Modが保持する体力・コントローラー・BossEventへの処理
4. ServerLevel、EntityTickList、セクション、追跡索引からの深層削除
5. 墓標UUIDによるEntity再登録の阻止

攻撃対象の補足にはサーバー側レイ判定を使用し、視線外のMobやブロックの背後にいるMobは取得しません。

既知の専用互換処理：

- Omni-Mobs 0.3.5.3
- The Trial Monolith
- Pig2

対象Modのjarはこのリポジトリに含まれません。

## 不変観測子

Curiosのartifactスロットへ装着するアイテムです。

- 通常ダメージ、死亡、強制削除への多層保護
- 体力、Air、Fire、凍結などの継続復元
- デバフ、放射線、拘束状態の除去
- サバイバル飛行と5段階の飛行速度
- 慣性抑制、段差補助、暗視の切り替え
- 64ブロック範囲のアイテム吸引
- ブロック／エンティティへの段階式リーチ延長
- Curios更新時に飛行状態を維持

操作キーはMinecraftのキー設定から変更できます。

## 観測進行

オフハンドのバニラ望遠鏡へ、最初に成立した一方の進行だけをNBT保存します。二種類の進行は混在できません。

- エンダードラゴン、ウィザー、ウォーデンの討伐を合計50回記録すると「終像鏡」へ変換
- 望遠鏡をオフハンド、トーテムをメインハンドに持って不死のトーテムを50回発動すると「原像鏡」へ変換
- 進行値をツールチップへ表示
- 完成品はバニラ望遠鏡の使用動作とモデルを維持

零位焦点は登録済みですが、入手方法と完成品レシピは未実装です。

## 描画

- 最終帰結と不変観測子は独自Core Shaderと加算合成を使用
- 透明な輪郭面を複数重ね、各層を独立して移動
- 不変観測子の装着中はプレイヤー背後へ板、レンズ、焦点環、破片を描画
- ツールチップの枠、文字色、選択中アイテム名をクライアント側でアニメーション

## 実装構成

- Forge Event Bus：通常のゲームイベントと装備機能
- Mixin：死亡状態、削除、Entity索引、クライアント表示への共通フック
- 埋め込みJava Agent：対象クラスの `getHealth`、`isAlive`、`isDeadOrDying` 系戻り値を強制死亡状態へ連動
- Reflection：既知Modの非公開状態と論理コントローラーへの互換処理
- Tombstone：深層削除後のUUID再登録をサーバー側で遮断
- GameTest：通常死亡、死亡拒否、深層削除、BossEvent、リーチ、プレイヤー保護、観測進行を自動検証

No-Sugar由来のMITライセンス部分と変更内容は [`CREDITS.txt`](CREDITS.txt) を参照してください。

## 開発用コマンド

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
```

ビルド成果物は `build/libs` に生成されます。

テスト用アイテム：

```mcfunction
/give @s parallax_axiom:final_conclusion
/give @s parallax_axiom:invariant_observer
/give @s parallax_axiom:zero_focus
/give @s parallax_axiom:original_image_mirror
/give @s parallax_axiom:terminal_image_mirror
```

観測進行を49回から確認する場合：

```mcfunction
/give @s minecraft:spyglass{ParallaxAxiomObservation:{Mode:"terminal",Count:49}}
/give @s minecraft:spyglass{ParallaxAxiomObservation:{Mode:"original",Count:49}}
```

## 注意

Mixin、自己アタッチ型Java Agent、内部フィールド書き換え、Entity索引の直接操作を使用します。導入前にワールドをバックアップしてください。

ライセンスはAll Rights Reservedです。Minecraft ForgeおよびMinecraft Coder Packに関する告知は [`LICENSE.txt`](LICENSE.txt) と [`CREDITS.txt`](CREDITS.txt) を参照してください。
