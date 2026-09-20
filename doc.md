# Fendai Multimodal & GPU Documentation

This document describes the architecture and usage of the Fendai native module, powered by the latest `llama.cpp`.

## Multimodal Support (mtmd)

The module now supports the **Multimodal (MTMD)** architecture, allowing the engine to process more than just text.

### Supported Modalities
- **Text**: Standard LLM interaction.
- **Vision**: Process images by loading a multimodal projection (`mmproj`) model.
- **Audio**: Experimental support for audio feature extraction (e.g., for models that can "hear").

### APIs
- `loadMmproj(path)`: Loads the vision/audio encoder.
- `getResponseAsFlowWithImage(prompt, imageBytes)`: Generates a response based on an image and a text prompt.

---

## GPU Acceleration (Vulkan)

Fendai supports **Vulkan** hardware acceleration for GPUs like Adreno and Mali.

### Prerequisites for Building
To enable GPU support, the build machine (Mac/Windows/Linux) must have:
1. **Vulkan SDK**: Provides `vulkan.hpp` and the `glslc` compiler.
2. **Shaders**: The file `llama.cpp/ggml/src/ggml-vulkan/ggml-vulkan-shaders.hpp` must be generated.

### How to Enable
1. Install the Vulkan SDK from [lunarg.com](https://vulkan.lunarg.com/).
2. Run the shader generator:
   ```bash
   cd fendai/llama.cpp/ggml/src/ggml-vulkan/vulkan-shaders
   mkdir build && cd build
   cmake .. && make
   ./vulkan-shaders-gen --target-hpp ../ggml-vulkan-shaders.hpp
   ```
3. Once the header is generated, the `CMakeLists.txt` will automatically detect it and enable `-DGGML_USE_VULKAN`.

### How it Works at Runtime
1. **Detection**: The engine logs all available backends at startup.
2. **Offloading**: Use `nGpuLayers` (e.g., `32`) in `InferenceParams` to move tensors to the GPU.
3. **Multimodal GPU**: Both the LLM and the vision encoder will use the GPU if offloading is enabled.

> [!TIP]
> Check Logcat for `Available backends: [..., Vulkan]`. If you only see `CPU`, the module was built without GPU support.

---

## Model Loading Troubleshooting

The model file is copied into the application's private `files` directory before it is loaded. The loader first reads the GGUF metadata, then initializes the native `llama.cpp` model.

### CPU Mode

`nGpuLayers = 0` is CPU-only mode. Fendai explicitly limits the native model device to CPU in this mode. Start with CPU mode when testing a new model or when Vulkan reports `Unsupported device`.

### GPU Mode

Set `nGpuLayers` to a positive number to offload that many layers to Vulkan. Use `-1` to request all layers. GPU support must be present in the native build, and the device GPU must support the model's tensor operations.

### Logcat Diagnostics

Use a clean log before selecting a model:

```bash
adb logcat -c
adb logcat -v brief -s Fendai-Cpp MODEL_SWITCH MainFendAI
```

Useful messages include:

- `GGUF read OK`: the file was copied and its metadata is readable.
- `loadModel() failed`: native model initialization failed; inspect the preceding `Fendai-Cpp` messages.
- `Unsupported device`: Vulkan was selected but cannot load one or more model tensors. Retry with `nGpuLayers = 0`.
- `Available backends: ... Vulkan ... CPU`: both backends are included in the build; this does not mean GPU mode is automatically safe.

Use a small GGUF model first and ensure the device has enough memory for the model and its context window. A large context size increases memory usage even when the model file itself is small.

---

## Build Requirements
- **Android NDK**: Version 26 or higher.
- **Vulkan SDK**: Required if you wish to recompile the shaders from source.
