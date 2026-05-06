---
marp: true
theme: default
paginate: true
backgroundColor: #0d0d0d
color: #f0f0f0
style: |
  section {
    font-family: 'Noto Sans TC', 'PingFang TC', sans-serif;
    padding: 48px 64px;
  }
  h1 { color: #7ecfff; font-size: 2.4em; }
  h2 { color: #7ecfff; border-bottom: 2px solid #7ecfff33; padding-bottom: 8px; }
  h3 { color: #aaddff; }
  strong { color: #ffdd88; }
  table { width: 100%; border-collapse: collapse; font-size: 0.85em; }
  th { background: #1a3a55; color: #7ecfff; padding: 8px 12px; }
  td { padding: 8px 12px; border-bottom: 1px solid #333; }
  blockquote { border-left: 4px solid #7ecfff; padding-left: 16px; color: #aaaaaa; font-style: italic; }
  code { background: #1a1a2e; color: #7ecfff; padding: 2px 6px; border-radius: 4px; }
  pre { background: #1a1a2e; padding: 20px; border-radius: 8px; }
  .lead { font-size: 1.3em; color: #cccccc; }
---

# YogaFlow 3D

## 視覺化智慧：重新定義居家瑜珈

<br>

**數據自主 • 即時引導 • 個性化成長**

<br>

> 讓科技守護隱私，讓 AI 成就專業。

---

## 問題：打破反饋黑箱

**為什麼 90% 的居家練習者無法持續進步？**

<br>

| 痛點 | 說明 |
|------|------|
| 影片練習的侷限 | 只有單向輸出，無法得知脊椎或關節角度是否安全 |
| 昂貴的私教成本 | 每小時收費昂貴，難以隨時隨地練習 |
| **核心盲區** | **自學者看不見自己的錯誤** |

---

## 解決方案：視覺化關節引導

**「看見」看不見的角度**

<br>

- **紅區警示**：關節偏離超過閾值時即時提醒
- **動態引導**：箭頭指示動作修正方向
- **實時疊加**：3D 骨架與影像合一

<br>

> 系統自動計算教練影片「標準角度」與使用者「當前角度」的毫秒級差異。

---

## 個性化身體基線追蹤

| 功能 | 說明 |
|------|------|
| **活動度評估** | 記錄每個關節的極限 ROM，建立獨特的身體檔案 |
| **疲勞感知** | 分析動作穩定度，適時建議休息以預防受傷 |
| **平衡分析** | 重心追蹤與雙側角度對比，優化核心穩定 |

---

## 目標差距動態分析

<br>

> 「目前您的關節活動度已提升 **25%**，距離目標英雄三式尚有 **12 度**的傾斜優化空間。」

<br>

**進步路徑可視化：邁向大師級標準**

系統不僅顯示「不對」，更分析你與「目標」的距離，將練習變成有趣的升級過程。

---

## 技術核心：Edge-AI 視覺引擎

MediaPipe · 33 關鍵點 3D 骨架 · 複雜姿勢穩定追蹤

<br>

```
CameraX  →  MediaPipe Pose  →  State Machine
                                     ↓
              TTS + 3D Avatar  ←  Flow Engine  →  Gemma LLM
```

<br>

- **Android on-device** — 無需雲端伺服器
- **< 1s 延遲** — 即時姿勢反饋
- **Godot 3D Avatar** — 視覺化教練示範

---

## 極致隱私：數據的主權在您

### 100% 離線推理 — 影像不離機

<br>

- ✅ 分析後即刻銷毀原始影像
- ✅ 完全不需要雲端訂閱
- ✅ 飛航模式下完美運行

<br>

**您的身體數據，永遠只屬於您。**

---

## 極簡練習流程

<br>

| 步驟 | 動作 |
|------|------|
| **01 匯入** | 上傳 YouTube 或手機收藏的大師影片 |
| **02 提取** | AI 自動分析教練動作，生成基準骨架 |
| **03 練習** | 即時視覺反饋與語音引導，糾正角度 |
| **04 成長** | 檢視目標差距報告，解鎖下個難度 |

---

## 競爭優勢分析

| 功能特點 | 傳統瑜珈 App | 真人私教 | **YogaFlow 3D** |
|----------|:-----------:|:-------:|:--------------:|
| 即時角度糾偏 | ✗ | ✓ | **✓** |
| 個性化成長軌跡 | ✗ | ✓ | **✓** |
| 隱私數據安全性 | 需上傳雲端 | 無記錄 | **100% 本地** |
| 單次練習成本 | 低 | 極高 | **零（買斷制）** |

---

## 未來的無限可能

<br>

**從瑜珈出發 →**

<br>

- 🏥 物理治療輔助系統
- 💃 舞蹈動作教學
- 🏃 全方位運動復健市場
- 🤸 個人化訓練計劃生成

---

## Agentic AI 開發流程

| 代理 | 角色 | 職責 |
|------|------|------|
| **Claude (PM)** | 專案經理 | GitHub Issues 分派、審查、提交 |
| **Codex** | 主要實作者 | 多檔案代碼修改、構建驗證、adb 測試 |
| **Gemini CLI** | 次要實作者 | Codex 受阻時接手、代碼審查 |

<br>

指令頻道：**GitHub Issues** + **Claude iMessage 頻道**

---

# Questions?

<br>

**讓科技守護隱私，讓 AI 成就專業。**

<br>

- 🔗 GitHub: [WangChengYeh/Yoga](https://github.com/WangChengYeh/Yoga)
- 📧 Email: contact@yogaflow3d.ai
