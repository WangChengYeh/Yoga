# YogaFlow 3D Pitch

> 視覺化智慧：重新定義居家瑜珈  
> 數據自主 • 即時引導 • 個性化成長

---

## 1. 打破反饋黑箱

為什麼多數居家練習者無法持續進步？

因為他們看不見自己的錯誤。

### 影片練習的侷限

傳統瑜珈影片只有單向輸出。練習者無法得知自己的脊椎是否過度彎曲、關節角度是否安全，或動作是否真的達到教練示範的標準。

### 昂貴的私教成本

高品質私人教練成本高，且需要預約時間，難以滿足隨時隨地練習的需求。

### 核心痛點

> 自學者最大的問題不是不努力，而是缺少即時、可視化、可量化的回饋。

---

## 2. 視覺化關節引導

### 「看見」看不見的角度

YogaFlow 3D 透過手機鏡頭分析使用者姿勢，將身體動作轉換為可量化的關節角度與骨架狀態。

系統可以比較：

```text
標準姿勢角度
        vs
使用者當前角度
```

並提供即時回饋。

### 核心能力

- 紅區警示：當關節偏離安全或目標範圍時提示使用者
- 動態引導：用語音與畫面提示修正方向
- 即時疊加：將骨架、角度與影像結合，讓錯誤變得可見

---

## 3. 個性化身體基線追蹤

每個人的柔軟度、關節活動度與穩定度都不同。

YogaFlow 3D 不只判斷「對或錯」，也建立個人化身體基線。

### 活動度評估

記錄每個關節的極限 ROM（Range of Motion），為每位使用者建立獨特的身體檔案。

### 疲勞感知

分析動作穩定度與姿勢波動，推估肌肉疲勞程度，適時建議休息，降低受傷風險。

### 平衡分析

透過重心追蹤與雙側角度對比，優化身體對稱性與核心穩定。

---

## 4. 進步路徑可視化

YogaFlow 3D 不只是提醒「你做錯了」，而是告訴你：

```text
你距離目標還差多少？
下一步該怎麼進步？
```

範例回饋：

> 目前您的關節活動度已提升 25%，距離目標英雄三式尚有 12 度的傾斜優化空間。

### 目標差距動態分析

系統分析使用者與目標姿勢之間的角度差距，將練習轉換成可追蹤、可量化、可升級的過程。

---

## 5. 技術核心：感知與理解的結合

YogaFlow 3D 的核心不是單純播放影片，而是即時理解使用者的身體狀態。

### Edge-AI 視覺引擎

利用 MediaPipe 進行高效能骨架抽取與 33 個關鍵點追蹤，支援側向、站姿、前彎、深蹲、橋式等姿勢分析。

### Flow DSL 驅動課程

課程不是由 LLM 隨機生成，而是由結構化 Flow JSON 定義：

```text
setup → movement → hold → correction → transition
```

每一步都可以指定：

- 教練提示語
- 偵測條件
- 關節角度門檻
- 穩定時間
- 修正語句

### 即時教練回饋

系統根據偵測結果決定：

```text
是否完成目前步驟？
是否需要修正？
是否可以進入下一步？
```

LLM 只負責語氣潤飾，不負責改變課程順序。

---

## 6. 極致隱私：數據主權在使用者

YogaFlow 3D 採用 local-first 設計。

### 100% 離線推理目標

影像分析與姿勢判斷在手機端完成，降低隱私風險。

### 隱私承諾

- 原始影像不需要離開裝置
- 姿勢分析可在本地完成
- 不依賴昂貴雲端訂閱
- 可朝飛航模式可用的方向設計

> 居家練習發生在高度私密的空間，AI 教練必須尊重資料主權。

---

## 7. 極簡練習流程

### 01 匯入 / 選擇課程

使用者選擇既有 Flow 課程，或未來透過影片輔助產生課程 Flow。

### 02 3D 姿勢分析

系統透過手機鏡頭提取使用者骨架與關節角度。

### 03 互動練習

即時視覺回饋與語音引導，協助使用者修正角度與穩定動作。

### 04 成長回饋

檢視目標差距、活動度變化與下一個訓練方向。

---

## 8. 競爭優勢分析

| 功能特點 | 傳統瑜珈 App | 真人私教 | YogaFlow 3D |
|---|---:|---:|---:|
| 即時角度糾偏 | 弱 | 強 | 強 |
| 個性化成長軌跡 | 弱 | 中 | 強 |
| 視覺化骨架分析 | 弱 | 弱 | 強 |
| 隱私數據安全性 | 常需雲端 | 無數據記錄 | 本地優先 |
| 單次練習成本 | 低 | 高 | 低 |
| 可隨時練習 | 強 | 弱 | 強 |

YogaFlow 3D 的定位：

```text
比影片 App 更懂你的身體
比真人私教更容易取得
比雲端 AI 更保護隱私
```

---

## 9. 未來的無限可能

YogaFlow 3D 從瑜珈開始，但底層能力可延伸到更大的 movement intelligence 市場。

### 延伸場景

- 物理治療
- 運動復健
- 舞蹈教學
- 健身動作修正
- 高齡活動度訓練
- 運動員姿勢分析

### 平台願景

> 建立一套可程式化、可量化、可解釋的 AI 動作教練平台。

---

## 10. 現階段產品落地狀態

目前 repo 已具備：

- Android app 架構
- CameraX camera pipeline
- MediaPipe pose integration
- Flow JSON DSL
- strict detection routing
- pose-specific detection mappers
- runtime tuning override
- numeric fail reason
- auto tuning suggestion
- LLM-assisted coach cue
- TTS voice output

目前仍在進行：

- CameraSetupController wiring cleanup
- Flow JSON CI validation
- runtime override persistence
- strict mountain mapper
- teacher-video-to-flow authoring pipeline

---

## 11. 關鍵產品原則

### Flow JSON 是課程真相來源

LLM 不應自由編排課程，不應新增影片中沒有的動作，也不應重排教學順序。

### 動作必須可量化

每個姿勢都應能被角度、穩定時間與偵測條件描述。

### 回饋必須可解釋

系統不只說「錯了」，也應說明：

```text
哪個角度錯？
差多少？
應該往哪裡修？
```

### 隱私是產品核心

使用者的影像與身體資料不應成為雲端黑箱。

---

## 12. Closing

YogaFlow 3D 的目標是讓每個人都能在家中獲得可視化、可量化、可持續進步的 AI 動作指導。

> 讓科技守護隱私，讓 AI 成就專業。

---

## Links

```text
GitHub: WangChengYeh/Yoga
Pitch deck: https://docs.google.com/presentation/d/1e0uUybgMie-YGJHSP8k9FjXeGxIeCdmMKvC8RVVI-nk/edit?usp=drivesdk
Email: contact@yogaflow3d.ai
```

---

## Image sources from original deck

```text
https://viso.ai/wp-content/uploads/2022/02/pose-estimation-human-ai-vision.png
https://www.droid-life.com/wp-content/uploads/2024/12/AEKE-K1-1400x933.jpg
https://www.mdpi.com/ai/ai-06-00180/article_deploy/html/images/ai-06-00180-g005.png
https://pxl-tcdie.terminalfour.net/fit-in/855x9999/filters:no_upscale()/filters:format(webp)/filters:quality(100)/prod01/channel_3/media/tcd/engineering/images/App-KineMo-Screenshot.png
```
