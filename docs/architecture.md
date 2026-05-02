# Architecture

## Layers

### 1. Perception Layer
- MediaPipe Pose Landmarker
- 33 keypoints tracking

### 2. Computation Layer
- Python 3.13 (Android)
- NumPy / SciPy
- 3D geometry & torque calculation

### 3. Intelligence Layer
- MediaPipe LLM
- Gemma-2B (quantized)

### 4. Output Layer
- Voice (TTS)
- 3D visualization

## Data Flow
Camera → Pose → Python → LLM → Voice / UI
