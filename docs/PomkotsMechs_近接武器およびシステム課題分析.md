# Pomkots Mechs (alpha.8) 最適化に関するご提案

## 1. 近接武器の当たり判定（AABB）の収縮問題：回転後に2つの対角点のみを取得しているため

すべての近接武器（Takao / Tsurugi / Kagenobu / Tenpou / Jinba / Gassan / Mitake）のダメージ判定が、以下の同じロジックで記述されています：

```java
var pilePos1 = new Vec3(6.5 * isRight(), 4.0F, 18F)
        .yRot((float) Math.toRadians(-getYRot()))
        .add(position());
var pilePos2 = new Vec3(-6.5 * isRight(), -4F, -4F)
        .yRot((float) Math.toRadians(-getYRot()))
        .add(position());

for (var ent : world.getEntities(null, new AABB(pilePos1, pilePos2))) {
    ...
}
```

### 【問題点】
`new AABB(p1, p2)` は、**2つの対角点**のみでmin/maxを判定しています。スイングの体積は本来、機体を中心に回転するボックスであるべきですが、機体がワールドの座標軸に対して斜めを向いている場合、これら2点は回転したボックスの「対角線の隙間」だけをカバーしてしまい、「完全なAABB（軸平行境界ボックス）」になりません。

**実機での検証：** 機体が約27度回転した状態だと、TakaoのダメージボックスはX軸方向で約1.4ブロック分の幅しか残らなくなります。そのため、正面数ブロック先にいるターゲットがボックスから完全に外れ、スイング全体でノックバックもダメージも発生しない現象が起こります（アニメーションとエフェクトは正常に再生されます）。つまり「当たっているように見えるのに何も起きない」状態となり、機体の向きによって確率的に発生してしまいます。

### 【改善案】
**すべての頂点（角）**を回転させてからmin/maxを取るか、ローカル座標のボックスに対して回転行列変換を行い、完全なAABBを求めることをお勧めします：

```java
// ローカルボックス：x ∈ {±6.5}（isRightで符号決定）、y ∈ {±4}、z ∈ {18, -4}
double angle = Math.toRadians(-getYRot());
double c = Math.cos(angle), s = Math.sin(angle);
double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
for (double x : new double[]{6.5 * isRight(), -6.5 * isRight()}) {
    for (double z : new double[]{18.0, -4.0}) {
        double rx = x * c + z * s;
        double rz = -x * s + z * c;
        minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
        minZ = Math.min(minZ, rz); maxZ = Math.max(maxZ, rz);
    }
}
AABB box = new AABB(
        position().x + minX, position().y - 4.0, position().z + minZ,
        position().x + maxX, position().y + 4.0, position().z + maxZ
);
```
※各武器によってローカルボックスのサイズが異なるため（例：Gassanの z は 28..36、Mitakeの x/y は非対称など）、武器ごとの実際の `pilePos1/pilePos2` に合わせて頂点を生成してください。

## 2. チャージ攻撃の判定が「1 tickの完全一致」に依存しており、処理漏れが発生しやすい

```java
public boolean isOnFire() {
    if (motion.getType().equals(Motion.MotionType.CONTINUOUS) && ...) {
        return true;
    } else if (motion.getType().equals(Motion.MotionType.CHARGE)) {
        return currentActionTick == fireStartTick + 5;   // この1フレームのみ true
    }
    ...
}
```

### 【問題点】
すべてのAOE処理（ダメージ、ノックバック、SE、ブロック破壊）が `currentActionTick == fireStartTick + 5` に完全に依存しています。クライアントとサーバーのアクション状態にラグが生じたり、フレームスキップが発生したり、アクションが他のロジックに割り込まれたりしてこの1フレームがスキップされると、スイング全体が無効になってしまいます。

### 【改善案】
- 1 tickの完全一致ではなく、区間判定（例：`currentActionTick >= fireStartTick + 5 && currentActionTick < fireStartTick + 5 + N` ※Nはアニメーション有効フレーム数、3～5など）に変更し、判定がアニメーションの命中フレームをカバーするようにする。
- または、1回限りのイベントフラグ（`fireAction()` で `firePending` を立てるなど）を導入し、アニメーションが命中フレームに達した時点で消費・決済する方式にする（tick数の正確な一致に依存しないようにする）。

## 3. 1 tickのみの静的AABB判定による、移動中のターゲットの判定漏れ

ダメージ判定が、発射される瞬間の1フレームでのみ、静的なAABB検索を行っています。スイングアニメーションは複数フレームにまたがるため、アニメーション中に移動してきたターゲットや、判定範囲に入ってきたターゲットを逃す可能性があります。

### 【改善案】
有効なスイングウィンドウ内で複数tickにわたって判定を行うか（上記の提案2と併用）、弾の命中判定と同じように、前フレームから現フレームにかけてのSwept AABB（掃引AABB）を使用して命中検出を行うことをお勧めします。

## 4. 単発の強攻撃がターゲットの無敵時間（20 tick）に完全に吸収されてしまう

`le.hurt(ds, damage)` が通常のダメージソースを使用しているため、ターゲットの無敵時間（`invulnerableTime > 0`）を貫通できません。近接攻撃は単発の高ダメージですが、スイング直前の1秒以内に別の攻撃（例：同じ機体の持続武器など）が当たっていた場合、この重い一撃が完全に無効化され、ノックバックしか発生しなくなります。

### 【改善案】
- 近接攻撃の決済には、無敵時間を貫通するダメージタイプ（`bypasses_invulnerability` など）を使用するか、命中の直前に個別にターゲットの `invulnerableTime` をリセットする。
- 少なくとも、ダメージ計算時に持続武器と同じ無敵時間のウィンドウを共有しないようにする。

## 5. 燃料/パーツ不足時に、実行中のチャージアクションが強制リセットされる

```java
private void resetAllActionsWhenNotActive() {
    if (this.isBroken() || !this.canWork(false) || !this.isVehicle()) {
        for (Action a : actionController.getAllActions()) {
            if (a.isInAction()) {
                a.reset();
            }
        }
    }
}
```

`canWork(false)` には `getFuelNow() != 0` のチェックが含まれています。チャージ中の武器の燃料がチャージウィンドウ内にゼロになると、アクションが静かにリセットされ、解放もフィードバックも行われません。プレイヤーから見ると「チャージを始めたのに永遠に発射されない」状態になります。

### 【改善案】
- 燃料不足時は直ちに `reset()` するのではなく、チャージ進捗を維持したまま警告（HUDやSE）を出すようにする。
- または、「新規アクションの開始不可」と「既存アクションの中断」を明確に区別し、本当に必要な場合のみアクションを中断する。

## 6. ネイティブAIによる driverInput の毎tick上書き問題（外部入力との競合）

`MechAutoController.tick()` が毎tick `mech.setDriverInput(currentInput)` を呼び出し、自身で計算したキー入力で全体を上書きしています。機体に対して外部からの操縦入力（プレイヤーや他のAIなど）が同時に存在する場合、入力が互いに上書きし合い、チャージ武器の「押し込み-離し」のトリガー（直前のフレームの入力状態に依存するもの）が失われる可能性があります。

### 【改善案】
- 外部入力に明確な優先度を持たせる（例：外部入力を優先し、ネイティブAIは空き入力のみを補完する）。
- または、「外部からの操縦入力がない」場合のみネイティブAIの武器ロジックを動かすようにする。

## 7. デバッグの利便性：当たり判定（AABB）の可視化

近接攻撃の判定収縮問題（提案1）は、実際のゲーム画面では肉眼で非常に見つけにくいです。クライアント側にデバッグ用のオプションを追加し、各近接武器の発射フレームのAABBを（シンプルなワイヤーフレーム等で）レンダリングできるようにすることをお勧めします。これにより、作者様やテスターが「判定範囲とアニメーションが一致しているか」を素早く確認できるようになります。