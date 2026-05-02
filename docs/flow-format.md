# Yoga Flow File Format

YogaFlow 使用「文字檔」來定義每一個瑜伽動作流程。

## 設計目標

- 可擴充（新增檔案即可新增動作）
- 可由 AI / 人類編輯
- 不需要改 code
- 可對齊 YouTube 教學

---

## 基本結構

```text
[FLOW]
metadata

[STEP 1]
...

[STEP 2]
...

[END]
```

---

## Metadata

```text
id = unique_id
name = 顯示名稱
pose = 對應 YogaPose
language = zh-TW
level = beginner
```

---

## Step

每個 step 包含：

```text
state = SETUP / MOVEMENT / HOLD / TRANSITION
duration_ms = 建議停留時間
cue = 教練語句（給 LLM 使用）
detect = 對應姿勢條件（未來 mapping）
correction = 修正策略
```

---

## END

```text
cue = 完成語句
```

---

## 未來擴充

- detect → 對應 PoseStateMachine
- cue → prompt 強化
- 多語言 flow
- AI 自動生成 flow
