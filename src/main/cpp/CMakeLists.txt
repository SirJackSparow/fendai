cmake_minimum_required(VERSION 3.22.1)
project("llama_android")

set(LLAMA_DIR_RELATIVE "../../../llama.cpp")
get_filename_component(LLAMA_DIR ${LLAMA_DIR_RELATIVE} ABSOLUTE)

set(GGML_DIR   ${LLAMA_DIR}/ggml)
set(COMMON_DIR ${LLAMA_DIR}/common)
set(VENDOR_DIR ${LLAMA_DIR}/vendor)

# ============================================================
# Generate version headers
# ============================================================
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/ggml-version.h"
        "#pragma once
#define GGML_VERSION \"unknown\"
#define GGML_COMMIT  \"\"
")

file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/llama-version.h"
        "#pragma once
#define LLAMA_VERSION \"unknown\"
#define LLAMA_COMMIT  \"\"
")

# ============================================================
# Sources (common to all ABIs)
# ============================================================
set(ANDROID_CHAT
        ${GGML_DIR}/src/ggml-alloc.c
        ${GGML_DIR}/src/ggml-backend.cpp
        ${GGML_DIR}/src/ggml-threading.cpp
        ${GGML_DIR}/src/ggml-quants.c
        ${GGML_DIR}/src/ggml-backend-reg.cpp
        ${GGML_DIR}/src/ggml-opt.cpp
        ${GGML_DIR}/src/ggml-cpu/ops.cpp
        ${GGML_DIR}/src/ggml-cpu/vec.cpp
        ${GGML_DIR}/src/ggml-cpu/traits.cpp
        ${GGML_DIR}/src/ggml-cpu/unary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/binary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
        ${GGML_DIR}/src/ggml.c
        ${GGML_DIR}/src/gguf.cpp

        ${LLAMA_DIR}/src/llama.cpp
        ${LLAMA_DIR}/src/llama-vocab.cpp
        ${LLAMA_DIR}/src/llama-grammar.cpp
        ${LLAMA_DIR}/src/llama-sampler.cpp
        ${LLAMA_DIR}/src/llama-context.cpp
        ${LLAMA_DIR}/src/llama-model.cpp
        ${LLAMA_DIR}/src/llama-model-loader.cpp
        ${LLAMA_DIR}/src/llama-impl.cpp
        ${LLAMA_DIR}/src/llama-memory.cpp
        ${LLAMA_DIR}/src/llama-memory-recurrent.cpp
        ${LLAMA_DIR}/src/llama-memory-hybrid.cpp
        ${LLAMA_DIR}/src/llama-mmap.cpp
        ${LLAMA_DIR}/src/llama-hparams.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-iswa.cpp
        ${LLAMA_DIR}/src/llama-kv-cache.cpp
        ${LLAMA_DIR}/src/llama-batch.cpp
        ${LLAMA_DIR}/src/llama-arch.cpp
        ${LLAMA_DIR}/src/llama-adapter.cpp
        ${LLAMA_DIR}/src/llama-chat.cpp
        ${LLAMA_DIR}/src/llama-graph.cpp
        ${LLAMA_DIR}/src/unicode.cpp
        ${LLAMA_DIR}/src/unicode-data.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-msa.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-dsa.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-dsa-iswa.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-dsv4.cpp
        ${LLAMA_DIR}/src/llama-memory-hybrid-iswa.cpp
        ${LLAMA_DIR}/src/llama-memory-hybrid-idx.cpp
        ${LLAMA_DIR}/src/llama-cparams.cpp
        ${LLAMA_DIR}/src/llama-io.cpp

        ${COMMON_DIR}/arg.cpp
        ${COMMON_DIR}/common.cpp
        ${COMMON_DIR}/console.cpp
        ${COMMON_DIR}/json-schema-to-grammar.cpp
        ${COMMON_DIR}/log.cpp
        ${COMMON_DIR}/ngram-cache.cpp
        ${COMMON_DIR}/sampling.cpp

        ${LLAMA_DIR}/tools/mtmd/mtmd.cpp
        ${LLAMA_DIR}/tools/mtmd/mtmd-helper.cpp
        ${LLAMA_DIR}/tools/mtmd/mtmd-audio.cpp
        ${LLAMA_DIR}/tools/mtmd/clip.cpp
        ${LLAMA_DIR}/tools/mtmd/deprecation-warning.cpp
        ${LLAMA_DIR}/tools/mtmd/mtmd-image.cpp

        ${GGML_DIR}/src/ggml-backend-dl.cpp
        ${GGML_DIR}/src/ggml-backend-meta.cpp
        ${GGML_DIR}/src/ggml-cpu/iqp.cpp

        ${LLAMA_DIR}/vendor/hash/hash.cpp
        ${LLAMA_DIR}/vendor/hash/sha256/sha256.c

        LLMInference.cpp
        llama_android.cpp
)

# Models
file(GLOB MODEL_FILES "${LLAMA_DIR}/src/models/*.cpp")
list(APPEND ANDROID_CHAT ${MODEL_FILES})

# mtmd vision models
file(GLOB MTMD_MODEL_FILES "${LLAMA_DIR}/tools/mtmd/models/*.cpp")
list(APPEND ANDROID_CHAT ${MTMD_MODEL_FILES})

# ============================================================
# Architecture-specific sources (MUST be before build_library)
# ============================================================
# Always need the generic quantize_row_* implementations
list(APPEND ANDROID_CHAT
        ${GGML_DIR}/src/ggml-cpu/quants.c
        ${GGML_DIR}/src/ggml-cpu/repack.cpp
)

if (${ANDROID_ABI} STREQUAL "arm64-v8a" OR ${ANDROID_ABI} STREQUAL "armeabi-v7a")
    list(APPEND ANDROID_CHAT
            ${GGML_DIR}/src/ggml-cpu/arch/arm/quants.c
            ${GGML_DIR}/src/ggml-cpu/arch/arm/repack.cpp
            ${GGML_DIR}/src/ggml-cpu/arch/arm/cpu-feats.cpp
    )
endif()

# ============================================================
# GGUF reader sources
# ============================================================
set(GGUF_READER_SOURCES
        ${GGML_DIR}/src/ggml.c
        ${GGML_DIR}/src/ggml-alloc.c
        ${GGML_DIR}/src/ggml-backend.cpp
        ${GGML_DIR}/src/ggml-threading.cpp
        ${GGML_DIR}/src/ggml-quants.c
        ${GGML_DIR}/src/ggml-backend-reg.cpp
        ${GGML_DIR}/src/ggml-opt.cpp
        ${GGML_DIR}/src/ggml-cpu/ops.cpp
        ${GGML_DIR}/src/ggml-cpu/vec.cpp
        ${GGML_DIR}/src/ggml-cpu/unary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/binary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
        ${GGML_DIR}/src/gguf.cpp
        GGUFReader.cpp
)

add_compile_options("-ffile-prefix-map=${LLAMA_DIR}=.")
add_link_options("LINKER:--build-id=none")
add_compile_definitions(GGML_DISABLE_CPU_AFFINITY)

function(build_library target_name)
    add_library(${target_name} SHARED ${ANDROID_CHAT})

    target_include_directories(${target_name} PUBLIC
            ${CMAKE_CURRENT_BINARY_DIR}
            ${COMMON_DIR}
            ${GGML_DIR}/include
            ${GGML_DIR}/src
            ${GGML_DIR}/src/ggml-cpu
            ${LLAMA_DIR}/include
            ${LLAMA_DIR}/src
            ${LLAMA_DIR}/src/models
            ${VENDOR_DIR}
            ${LLAMA_DIR}/tools/mtmd
            ${LLAMA_DIR}/vendor/hash
    )

    target_compile_options(${target_name} PUBLIC
            -fvisibility=hidden
            -fvisibility-inlines-hidden
            -ffunction-sections
            -fdata-sections
    )

    target_link_libraries(${target_name} android log dl)

    target_link_options(${target_name} PRIVATE
            -Wl,--gc-sections
            -flto
            -Wl,--exclude-libs,ALL
            -Wl,--allow-multiple-definition
            "-Wl,-z,max-page-size=16384"
    )
endfunction()

function(build_library_arm64 target_name cpu_flags)
    build_library(${target_name})
    target_compile_options(${target_name} PUBLIC
            -DGGML_USE_CPU -DGGML_USE_CPU_AARCH64 ${cpu_flags} -O3
    )
endfunction()

function(build_library_armv7a target_name cpu_flags fpu fpu_abi)
    build_library(${target_name})
    target_compile_options(${target_name} PUBLIC
            -DGGML_USE_CPU ${cpu_flags} ${fpu} ${fpu_abi} -O3
    )
endfunction()

function(build_library_universal target_name)
    build_library(${target_name})
    target_compile_options(${target_name} PUBLIC -DGGML_USE_CPU -O3)
endfunction()

# ============================================================
# Build targets
# ============================================================
build_library_universal("llama_android")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    build_library_armv7a("llama_android_v7a" "-march=armv7-a" "-mfpu=neon-vfpv4" "-mfloat-abi=softfp")
endif()

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_library_arm64("llama_android_v8" "-march=armv8-a")
    build_library_arm64("llama_android_v8_2_fp16" "-march=armv8.2-a+fp16")
    build_library_arm64("llama_android_v8_2_fp16_dotprod" "-march=armv8.2-a+fp16+dotprod")
    build_library_arm64("llama_android_v8_4_fp16_dotprod" "-march=armv8.4-a+fp16+dotprod")
    build_library_arm64("llama_android_v8_4_fp16_dotprod_sve" "-march=armv8.4-a+fp16+dotprod+sve")
    build_library_arm64("llama_android_v8_4_fp16_dotprod_i8mm" "-march=armv8.4-a+fp16+dotprod+i8mm")
    build_library_arm64("llama_android_v8_4_fp16_dotprod_i8mm_sve" "-march=armv8.4-a+fp16+dotprod+i8mm+sve")
endif()

# ============================================================
# GGUF reader
# ============================================================
set(TARGET_NAME_GGUF_READER ggufreader)
add_library(${TARGET_NAME_GGUF_READER} SHARED ${GGUF_READER_SOURCES})

target_include_directories(${TARGET_NAME_GGUF_READER} PUBLIC
        ${CMAKE_CURRENT_BINARY_DIR}
        ${GGML_DIR}/include
        ${GGML_DIR}/src
        ${GGML_DIR}/src/ggml-cpu
        ${LLAMA_DIR}/vendor/hash
)

target_compile_options(${TARGET_NAME_GGUF_READER} PUBLIC
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -ffunction-sections
        -fdata-sections
        -DGGML_USE_CPU
        -O3
)

target_link_libraries(${TARGET_NAME_GGUF_READER} android log dl)

target_link_options(${TARGET_NAME_GGUF_READER} PRIVATE
        -Wl,--gc-sections
        -flto
        -Wl,--exclude-libs,ALL
        -Wl,--allow-multiple-definition
        "-Wl,-z,max-page-size=16384"
)