# YogaFlow 3D

> **自己的資訊，自己掌控。數據零離機，專業不妥協。**

YogaFlow 3D 是一款針對現代隱私需求與個人化運動指導開發的智慧瑜珈教練系統。專案目標是結合 Android 本地端 AI、3D 姿勢分析、Python 科學運算與本地 LLM 推理，打造一個「影像不離機」的智慧瑜珈教練。

## 產品定位

**YogaFlow 3D 只支援高階 Android 手機。**

本專案不追求低階或中階手機相容性。核心設計優先順序如下：

1. 本地端 AI 推理
2. 即時 3D 姿勢分析
3. 本地語音教練
4. 隱私資料零離機
5. 低延遲互動體驗

若裝置效能不足，App 應明確提示「此裝置不支援完整本地 AI 教練模式」，而不是降級成雲端或低品質體驗。

## 建議最低裝置條件

- Android 15 或以上優先
- 旗艦級 SoC / NPU
- RAM 12GB 以上建議
- 支援 GPU / NNAPI / 高效能本地推理
- 可穩定執行 CameraX + MediaPipe Pose + TTS + LLM

## 核心特色

- **極致隱私**：影像與姿勢資料只在手機本地端處理，不上傳雲端。
- **3D 姿勢分析**：使用 MediaPipe Pose Landmarker 追蹤 33 個 3D 關節關鍵點。
- **本地科學運算**：以 Python 3.13 Android 原生環境搭配 NumPy / SciPy 進行幾何與生物力學分析。
- **本地 LLM 教練**：透過 MediaPipe LLM Inference API 與量化 Gemma 模型，把姿勢數據轉成即時語音指導。
- **Agentic AI 研發**：使用 Coding Agent、Orchestration Agent 與自動化測試，加速演算法與產品迭代。

## 初始技術架構

```text
Camera / Sensor Input
        ↓
MediaPipe Pose Landmarker
        ↓
3D Joint Data
        ↓
Native Python Engine
NumPy / SciPy Geometry Analysis
        ↓
Local LLM Coach Reasoning
        ↓
TTS / Voice Feedback + 3D Skeleton Preview
```

## Repo 結構

```text
.
├── README.md
├── docs/
│   ├── project-plan.md
│   ├── architecture.md
│   └── roadmap.md
└── .gitignore
```

## Roadmap

1. **Phase 1 - MVP**：建立 Android Python 環境，完成 3D 關節點提取。
2. **Phase 2 - Logic**：整合 NumPy 運算與 MediaPipe LLM 本地推理。
3. **Phase 3 - Agentic**：擴展 100+ 瑜珈動作庫與自動化測試。
4. **Phase 4 - Launch**：產品上市，主打隱私與 3D 建模。

## Tags

`#Android15` `#Python313` `#MediaPipe` `#LocalLLM` `#AgenticAI` `#HighEndAndroidOnly`
