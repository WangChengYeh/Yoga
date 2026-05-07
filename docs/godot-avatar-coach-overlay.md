# Godot Avatar Coach Overlay 設計草案

## 目標

在現有 Yoga Android/Kotlin 架構上，加入一個具象化的 3D Avatar 教練。

核心方向不是重寫整個 App，而是：

- 保留現有 Kotlin camera / MediaPipe / pose detection / skeleton overlay / step engine / coach decision
- 只把「教練的可視化身體」交給 Godot
- 將 Godot Avatar 疊在 camera view 上，形成類似真人教練在旁邊示範與提醒的效果

產品主張：

```text
名模一對一教你
```

因此 Avatar 的定位要偏向「名模型 / 運動品牌廣告感」的高吸引力教練，而不是一般制式瑜伽老師。

---

## 架構原則

### Kotlin 保留為主控端

Kotlin / Android 仍然負責：

- Camera preview
- MediaPipe pose detection
- 使用者 skeleton overlay
- pose angle 計算
- step engine
- coach decision
- correction 判斷
- TTS 或文字提示

### Godot 只做 Avatar 表現層

Godot 只負責：

- 3D Avatar rendering
- 教練動畫
- 動作過渡
- IK 微調
- 視覺化 correction feedback

Godot 不負責：

- 不重新做 camera
- 不重新做 pose detection
- 不重新做 step engine
- 不重新做主要 coach decision

一句話：

> Kotlin 是腦，Godot 是教練的身體。

---

## Avatar Skin 方向

### 角色定位

第一版 Avatar 採用 **Model-fit / Editorial Fitness** 方向。

目標是讓教練具有高吸引力、名模般的身體線條與運動品牌廣告感，同時保留足夠的瑜伽教練功能性：

```text
名模一對一教你
```

核心比例：

```text
30% 專業瑜伽教練
70% 名模 / 運動品牌廣告感
```

設計目的：

- 提升第一眼吸引力
- 讓使用者更有意願跟著練
- 讓角色本身具有「想跟著她一起做」的動機
- 讓身體線條、關節、髖部、膝蓋、肩膀更容易觀察
- 保留最低限度的教練可信度與動作示範準確度

角色不是普通教練，而是：

```text
像運動品牌廣告中的名模瑜伽教練
```

### 身形設定

建議：

- 女性瑜伽教練
- 修長比例，接近名模體態
- 健康、緊實、線條明確
- 腰臀比例明顯但自然
- 腹部可有輕微線條
- 腿部線條清楚，方便示範站姿、前彎、伸展
- 肩頸線條乾淨，方便看手臂與上半身姿勢
- 整體要有運動廣告的視覺吸引力

避免：

- 過度健美，像健美比賽選手
- 過瘦或不健康比例
- 過度誇張到影響瑜伽動作可信度

### 服裝設定

建議第一版：

- 高強度專業運動內衣（Sport Bra Top）
- 高腰貼身 leggings
- 赤腳，符合瑜伽練習情境
- 無大 logo 或過多圖案
- 服裝以貼身、清楚呈現身體線條為主

色彩建議：

- 黑色
- 深灰
- 深藍
- 低飽和中性色

原因：

- 不干擾 camera 主畫面
- 動作線條清楚
- 讓使用者專注在姿勢與教練示範
- 讓名模感偏向高級運動廣告，而不是廉價視覺刺激

### 臉部與氣質

建議：

- 冷靜、自信、專注
- 表情少但有存在感
- 眼神穩定，有一對一帶練感
- 避免過度甜美或過度誇張的表情
- 髮型以馬尾、綁髮或簡潔短髮為主，避免遮擋肩頸與上半身線條

整體氣質：

```text
30% 專業瑜伽教練
70% 名模 / 運動品牌廣告感
```

### 動作呈現原則

Model-fit 的吸引力不只來自 skin，也來自動作控制。

Godot Avatar 應加入：

- 動作進出使用 easing，不要瞬間切換
- hold 時加入呼吸微動作
- 重心有極小幅度自然調整
- 手指與肩膀不要僵硬
- 頭部偶爾微微朝向使用者
- correction 時用清楚示範，不要只做姿勢切換
- 動作節奏要像廣告拍攝中的 controlled movement：慢、穩、有線條

### 可落地 Avatar Pipeline

建議：

1. 使用 Ready Player Me 或其他 glTF / GLB avatar 來源
2. 建立女性 athletic / model-like body
3. 服裝選 Sport Bra + high-waist leggings
4. 匯出 `.glb`
5. 匯入 Godot 4.x
6. 使用 Mixamo 或自製瑜伽動畫作為 base animation
7. 在 Godot 中加 breathing、IK、easing、highlight

---

## 高層資料流

```text
Camera
  ↓
MediaPipe Pose Detection
  ↓
Pose landmarks / angles
  ↓
Kotlin Yoga Engine
  ├─ Step Engine
  ├─ Pose Rule / Error Mapping
  └─ Coach Decision
  ↓
PoseCoachFrame JSON
  ↓
Godot Avatar View
  ├─ Play / blend animation
  ├─ Apply IK / body adjustment
  └─ Show visual feedback
```

---

## UI 方向

第一版建議採用 overlay / picture-in-picture。

```text
┌────────────────────────┐
│                        │
│   Camera View           │
│   + user skeleton       │
│                        │
│              ┌───────┐ │
│              │Godot  │ │
│              │Coach  │ │
│              │Avatar │ │
│              └───────┘ │
└────────────────────────┘
```

建議設定：

- Camera view 全螢幕
- 使用者 skeleton overlay 保留
- Godot Avatar 放右下或左下
- Avatar 大小約畫面 20%–30%
- 第一版先用矩形小視窗，不急著做透明背景
- 第二版再嘗試透明背景，讓 Avatar 像浮在 camera 上

---

## Android Layout 概念

```text
FrameLayout
 ├─ CameraPreviewView
 ├─ SkeletonOverlayView
 └─ GodotAvatarView
```

GodotAvatarView 可以先作為固定大小 overlay：

- width: 25% screen width
- height: 25%–35% screen height
- gravity: bottom | end
- margin: 16dp

---

## 傳給 Godot 的資料格式

建議 Kotlin 每次 pose update 後，整理成一個統一 JSON。

```json
{
  "timestampMs": 1710000000000,
  "stepId": "forward_fold_setup",
  "phase": "hold",
  "pose": {
    "leftKneeAngle": 168,
    "rightKneeAngle": 165,
    "hipAngle": 82,
    "spineAngle": 18,
    "ankleDistanceRatio": 1.7
  },
  "coach": {
    "state": "ok",
    "error": null,
    "message": "很好，停在這裡，保持呼吸。",
    "severity": 0
  },
  "avatar": {
    "action": "hold_forward_fold",
    "emotion": "calm",
    "highlight": null
  }
}
```

錯誤範例：

```json
{
  "timestampMs": 1710000000500,
  "stepId": "forward_fold_setup",
  "phase": "hold",
  "pose": {
    "leftKneeAngle": 142,
    "rightKneeAngle": 148,
    "hipAngle": 75,
    "spineAngle": 28,
    "ankleDistanceRatio": 1.6
  },
  "coach": {
    "state": "needs_correction",
    "error": "knees_bent",
    "message": "我看到你的膝蓋有點彎，再打直一點點。",
    "severity": 2
  },
  "avatar": {
    "action": "correct_knees",
    "emotion": "focused",
    "highlight": "knees"
  }
}
```

---

## Kotlin 端責任

Kotlin 每個 frame 或固定頻率執行：

1. Camera frame 進 MediaPipe
2. 取得 landmarks
3. 計算 angles
4. 根據 current step 做 rule evaluation
5. 產生 coach state
6. 更新 skeleton overlay
7. 傳 PoseCoachFrame 給 Godot

Pseudo code：

```kotlin
fun onPoseDetected(landmarks: PoseLandmarks) {
    val pose = poseAnalyzer.computeAngles(landmarks)
    val step = stepEngine.currentStep
    val decision = coachDecisionEngine.evaluate(step, pose)

    skeletonOverlay.update(landmarks, decision)

    val frame = PoseCoachFrame(
        timestampMs = System.currentTimeMillis(),
        stepId = step.id,
        phase = step.phase,
        pose = pose,
        coach = decision.toCoachState(),
        avatar = decision.toAvatarCommand()
    )

    godotBridge.send(frame)
}
```

---

## Godot 端責任

Godot 收到 PoseCoachFrame 後：

1. 解析 JSON
2. 根據 avatar.action 播放或混合動畫
3. 根據 highlight 顯示局部提示
4. 根據 severity 調整表現強度
5. 必要時用 IK 微調手、腳、頭、脊椎方向

Pseudo code：

```gdscript
func on_pose_coach_frame(frame):
    var action = frame.avatar.action
    var highlight = frame.avatar.highlight
    var severity = frame.coach.severity

    avatar_controller.play_action(action)
    avatar_controller.apply_micro_motion(frame.pose)
    avatar_controller.set_highlight(highlight, severity)
```

---

## 溝通方式

第一版建議使用 WebSocket 或 Godot Android plugin bridge。

### 選項 A：WebSocket

優點：

- 邏輯清楚
- Kotlin / Godot 解耦
- 容易 debug

缺點：

- 多一層通訊
- Android 本機整合要注意生命週期

### 選項 B：Godot as Android Library / Plugin bridge

優點：

- Android 內整合較直接
- 可以把 Godot 當成 view 疊進原生畫面

缺點：

- 初期設定較麻煩
- 需要處理 Godot 與 Activity lifecycle

### 最終決定：Hybrid 混合架構 (Godot Android Library + Local Loopback WebSocket)

我們最終選擇了混合兩者優點的最佳解法：
- 使用 **GodotFragment** 將 Godot 直接嵌入 Android APK 中 (選項 B)，達成無縫整合。
- 保留 **WebSocket** 通訊 (選項 A)，但改走 local loopback (`127.0.0.1`) 進行 IPC。
這樣既免除了 JNI / Plugin bridge 複雜的 C++ 與設定負擔，又讓 Kotlin 和 Godot 在同一個 App 內保持完美解耦與極速通訊。

---

## 實作階段

### Phase 1：保留原畫面，先加資料輸出

- Camera + skeleton overlay 不動
- Kotlin 產生 PoseCoachFrame
- 先印 log，不接 Godot

完成標準：

- 每個 frame 或每 200ms 可看到 step / pose / coach state log

---

### Phase 2：Godot 小視窗顯示 Avatar

- Android 畫面疊一個 Godot view
- Godot 先播放 idle / demo animation
- 不接 pose

完成標準：

- Camera 畫面正常
- Skeleton overlay 正常
- Godot Avatar 可在右下角顯示

---

### Phase 3：Kotlin 傳 command 給 Godot

- Kotlin 傳 stepId / action / error
- Godot 根據 action 切換動畫

完成標準：

- knees_bent 時 Avatar 做 correction gesture
- ok 時 Avatar 回到 calm hold animation

---

### Phase 4：加入 IK 與真人感

- Avatar 動作加入 easing
- hold 時加入 breathing motion
- 頭部微微朝向使用者
- 錯誤部位 highlight

完成標準：

- Avatar 不像機器切換姿勢
- correction 有觀察與示範感

---

### Phase 5：透明 overlay / 更像 AR

- 嘗試 Godot 透明背景
- Avatar 直接浮在 camera view 上
- 必要時加入陰影或地面定位

完成標準：

- Avatar 能像疊在真實 camera 畫面上
- 不遮擋使用者主要骨架

---

## 第一個驗證動作：Forward Fold

建議先用 Forward Fold 驗證完整流程。

### Pose rule example

```text
if kneeAngle < 160:
    error = knees_bent
else:
    error = ok
```

### Coach behavior

- ok：Avatar 保持 forward fold 示範，輕微呼吸
- knees_bent：Avatar 指向膝蓋或示範腿伸直
- too_fast：Avatar 放慢動作，做 slow-down gesture
- not_deep_enough：Avatar 示範慢慢往前折

---

## 目前決策

採用：

```text
Kotlin camera / pose / skeleton / decision 全保留
Godot 只做 Avatar Coach overlay
```

原因：

- 最小改動
- 不破壞現有 Yoga repo
- 保留目前 camera 與 MediaPipe 投資
- 可以快速讓 AI 教練具象化
- 後續仍可逐步升級成更完整的 3D / AR 體驗

---

## Avatar 自動迴避功能（Issue #54）

### 問題

Avatar 直接疊在使用者身上，遮擋骨架與姿勢視線，無法提供教練應有的「在旁示範」效果。

### 解法：`screen_side` 欄位

每個 `PoseCoachFrame` 的 `avatar` 物件加入 `screen_side` 欄位（`"left"` 或 `"right"`），Godot Avatar 根據此值將自己移到人體的對側：

```json
{
  "avatar": {
    "action": "hold_mountain",
    "emotion": "calm",
    "highlight": null,
    "screen_side": "left"
  }
}
```

### Android 端：`humanScreenSide()`

```kotlin
private fun humanScreenSide(frame: PoseDetectionResult): String {
    // PoseHelper 已對前鏡頭翻轉 x，直接使用即是螢幕座標
    val screenX = frame.imageLandmarks.getOrNull(0)?.x()   // landmark 0 = 鼻子
        ?: ((frame.imageLandmarks.getOrNull(11)?.x() ?: 0.5f) +
            (frame.imageLandmarks.getOrNull(12)?.x() ?: 0.5f)) / 2f
    // 人在左 → avatar 去右；人在右 → avatar 去左
    return if (screenX < 0.5f) "right" else "left"
}
```

回傳值是 **avatar 的目標側**，不是人體所在側。

### Godot 端：`apply_screen_side()`

```gdscript
var _side_x_offset: float = 0.4

func apply_screen_side(side: String) -> void:
    match side:
        "left":  _side_x_offset = -0.6
        "right": _side_x_offset =  0.6
        _:       _side_x_offset =  0.0
    var tween = create_tween()
    tween.tween_property(self, "position:x",
        _base_position.x + _side_x_offset, 0.4)
```

在 Godot 3D 座標系中，正 x 對應螢幕右側（標準方向），故 `+0.6` 移到右側、`-0.6` 移到左側。

### 已知限制

- 使用者做前彎、趴臥等姿勢時身體橫跨整個畫面，此時任一側都可能有部份重疊，這是可接受的邊界情況。
- 目前偏移量為 ±0.6 Godot 單位，若仍嫌不夠遠可在 `apply_screen_side()` 中調大。

### GDScript 部署注意事項

Godot 匯出時會將 `.gd` 來源編譯成 `.gdc` 二進制檔（位於 `app/src/main/assets/scripts/`）。**修改 `godot/scripts/*.gd` 後必須同步更新 assets 目錄，否則裝置執行的仍是舊版 bytecode。**

目前採用的做法：將更新後的 `.gd` 來源直接複製到 `app/src/main/assets/scripts/`，並把對應的 `.gd.remap` 指向 `.gd` 而非 `.gdc`，讓 Godot debug runtime 載入來源檔。

---

## 待確認事項

- Godot 版本：建議 Godot 4.x
- Avatar 格式：建議 glTF / GLB
- 動畫來源：可先用 Mixamo 或自製簡單動畫
- Android 整合方式：先 WebSocket demo，之後再評估 Godot Android library
- UI 位置：第一版右下角 picture-in-picture
