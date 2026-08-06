# Changelog

## 0.15.0

- Add a custom textureless tooltip renderer for Final Conclusion and Invariant Observer
- Render a translucent black-mirror panel with animated iridescent thin-film borders
- Add displaced ghost contours, cursor-dependent glass seams, incomplete lens pixels and extended corner brackets
- Extend animated elliptical lenses, skewed panes and detached film fragments beyond both sides of the tooltip
- Preserve vanilla tooltip components, Shift detail switching and screen-edge positioning

## 0.14.1

- Resolve at most one server-authoritative look target during each Final Conclusion swing
- Mark a normal direct attack as that swing's selected target so fallback scanning cannot acquire another mob
- Detect rapid animation restarts while preserving unpickable-mob targeting and the vanilla inventory-tick fallback

## 0.14.0

- Adopt Parallax Axiom as the public mod name while preserving the ultimatum namespace for compatibility
- Rename Absolute End to Final Conclusion and Absolute Artifact to Invariant Observer
- Replace technical item descriptions with compact two-line observation lore in Japanese and English
- Show one concise functional explanation only while Shift is held
- Rename the keybind category and controls without changing existing bindings

## 0.13.3

- Enlarge the outer incomplete focus ring from 0.43 to 0.69 blocks in local radius
- Enlarge the inner ring from 0.29 to 0.45 blocks and increase segment detail
- Thicken both ring films slightly so the expanded outlines remain legible

## 0.13.2

- Reduce maximum visible follow separation from roughly 1.5 blocks to one block
- Remove the eight-block internal emergency snap that caused repeated flicker during extreme flight
- Clamp every follower's stored anchor continuously so fast movement holds a stable trailing position

## 0.13.1

- Replace spring overshoot with monotonic delayed interpolation that settles without passing the wearer
- Give every pane its own deterministic position and rotation delay instead of scaling one shared motion
- Apply separate stable delay profiles to both focus rings and all 22 film fragments
- Allow delayed parts to trail by up to roughly 1.5 visible blocks in any direction

## 0.13.0

- Add spring-based delayed following for the observer array using interpolated world position and body rotation
- Let the structure trail movement, overshoot slightly after abrupt stops and settle back into place
- Give central panes restrained lag while outer panes, rings and fragments respond more independently
- Clamp translation and rotation lag so teleports and extreme flight speeds cannot detach the effect

## 0.12.5

- Move 18 film fragments into loose bands beyond the left and right pane edges
- Place the remaining four fragments above and below the pane group
- Clear the center so fragment silhouettes no longer obscure the layered glass design

## 0.12.4

- Expand the observer array from 10 to 22 independently drifting film fragments
- Mix seven fragment scales from tiny splinters to large irregular shards
- Vary fragment height, distance, depth, aspect ratio and rotation speed to avoid a uniform ring

## 0.12.3

- Lower the observer array another 0.15 blocks behind the wearer

## 0.12.2

- Give every observer pane its own subtle horizontal drift, yaw and roll cycle
- Offset each pane's timing so the array searches independently instead of swaying as one rigid object

## 0.12.1

- Enlarge the observer array by 25 percent and lower it behind the wearer
- Keep its asymmetric pane layout during attacks instead of straightening or converging it
- Retain only a brief iridescent intensity pulse as attack feedback

## 0.12.0

- Added the first textureless visual manifestation for the Absolute Artifact
- Render layered transparent panes, incomplete focus rings and iridescent film fragments behind the wearer
- Shift internal layers and visibility with the camera angle to create parallax without borrowing Fantasy Ending's starfield motif
- Converge the floating array for a short instant when Absolute End attacks an entity

## 0.11.3

- Replaced the X emergency-stop control with a persistent step-assist toggle
- Keep one-block step assist enabled by default and remove its modifier cleanly when disabled or unequipped

## 0.11.2

- Preserve active flight when Curios represents an Artifact NBT setting update as an unequip/equip swap
- Only restore the original flight state when the Artifact slot actually becomes empty or changes item

## 0.11.1

- Prevent the Absolute Artifact's movement-speed modifier from inflating camera FOV
- Preserve vanilla sprint, flight, bow and other non-Artifact FOV behavior

## 0.11.0

- Added five persistent flight-speed stages with the existing sprint boost capped at absolute speed
- Added an immediate aerial brake and default-on client-side inertia canceling
- Added toggleable hidden night vision and a 64-block item magnet
- Reject vanilla freezing, powder-snow and stuck-in-block movement restraints through events and Mixins
- Added independent, rebindable V/X/C/N/B controls while retaining R for staged reach

## 0.10.0

- Reject harmful effects at application time and purge direct hostile effect-map writes every tick
- Added persistent reach stages: standard, 8, 16, 32 and 64 blocks
- Added an R keybind and action-bar feedback for cycling reach
- Unified Forge block/entity reach and Absolute End's unpickable-target resolver

## 0.9.0

- Added fuel-free survival flight while the Absolute Artifact is equipped
- Added sprint-controlled flight boost with automatic ability synchronization
- Added doubled ground speed, tripled swim speed and one-block step assist
- Restore the player's original flight permission and speed when the artifact is removed

## 0.8.0

- Added the invisible Absolute Artifact for a dedicated Curios artifact slot
- Moved absolute player invincibility from holding Absolute End to equipping the artifact
- Made the artifact persist through death and exposed a stable equipped-state hook for future visual manifestations
- Added Curios 5.14.1 as a required dependency

## 0.7.1

- Delay the vanilla death POOF event until the corpse animation finishes, immediately before final removal
- Emit the death POOF once, matching vanilla, instead of twice at forced-death start

## 0.7.0

- Rebuilt universal forced death around NoSugar's MIT-licensed execution ordering and client erased-state model
- Added an embedded Java agent that hooks every getHealth/isAlive/isDeadOrDying override when NoSugar's agent is absent
- Force death loot, LivingDeathEvent, death sound, strong-hit sound and death particles before the delayed erase
- Added a client death packet and deathTime clock so protected mobs render the normal red/falling animation for 21 ticks
- Strengthened final client-index erasure and prioritize LivingEntity targets over protected projectiles

## 0.6.1

- Route every modded LivingEntity, including Trial/Invader Monolith, directly through the universal animated-death kernel without a hostile hurt probe
- Resolve left-click targets on the server without trusting Entity.isPickable, so attack-packet-resistant mobs still enter the generic pipeline
- Align resistant-monolith presentation with NoSugar ordering: immediate death state and boss bar 0, followed by final erasure after 21 ticks

## 0.6.0

- Mod名・クラス名・フィールド名に依存しない論理Controller/Proxy検出器を追加
- Proxy→Controller、Controller→Proxy、static registry登録の三条件で非Entity本体を安全に認定
- 認定したControllerをstatic Collection/Mapから除去し、終了フラグ、BossEvent、全Proxyを汎用消去
- Metapotent Flashfur専用アダプターを実行経路から撤去し、新しい汎用論理Entity層へ統合
- static registry直下に加え、static Map/List→level Handler→Controller Listの2段階管理を検出
- Tesseract BeastをTrial専用アダプターから汎用論理Entity層へ移行

## 0.5.4

- Omni専用アダプターをMetapotent Flashfurの非Entityコントローラーだけに限定
- Alarm、Silverlight、通常Flashfurを未知Mod Mobと同じ汎用死亡核へ統合
- Modded LivingEntityは危険な`hurt()`を呼ばず、死亡・ドロップ・21tick死亡演出・最終消去を汎用実行
- ボスバーを即削除せず、HP 0表示から死亡演出後に本体と同時削除する順序へ変更

## 0.5.3

- Omni-Mobsの死亡成立直後にserver tick listから隔離し、反撃・移動だけを即停止
- クライアント追跡は20tick維持して、赤くなって倒れる本来の死亡演出を復元
- 死亡演出終了後に決定論的最終消去を実行

## 0.5.2

- Alarm、FlashfurなどOmni-Mobsの`hurt()`反撃経路を呼ばず、汎用死亡核へ直接移行
- 汎用死亡、Omni復活元解除、決定論的最終消去を同一server tick内で完了
- 25tickの確認待ち中に移動・反撃できていた時間を解消

## 0.5.1

- Omni-Mobsの通常死亡成立直後にクライアント表示とボスバーを消し、約20tickの見かけ上の生存時間を解消
- サーバー側の本物の死亡処理、ドロップ、Modコールバックは従来どおり完走
- 通常Mobのバニラ死亡演出は変更なし

## 0.5.0

- NoSugar 1.9.1のEntityLookup差し替え方式を独自実装し、`byId` / `byUuid`だけでなくtick list、全EntitySection、known UUID、ChunkMapも決定論的に再構築
- 最終削除UUIDをワールドSavedDataへ永続化し、再起動後の復活、tick list再登録、チャンク保存を拒否
- 専用Mod名やフィールド名に依存しないBossEvent探索を追加し、未知のボスバーにも削除パケットを送信
- クライアント側のEntityLookup、tick list、全EntitySectionも差し替え、同一UUIDの再登録を拒否
- 従来どおり通常Mobは通常死亡を優先し、上記の侵襲的処理は全死亡手段を拒否した対象にだけ適用

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
