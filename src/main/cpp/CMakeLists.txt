cmake_minimum_required(VERSION 3.22.1)
project("llama_android")

set(LLAMA_DIR_RELATIVE "../../../llama.cpp")
get_filename_component(LLAMA_DIR ${LLAMA_DIR_RELATIVE} ABSOLUTE)

set(GGML_DIR ${LLAMA_DIR}/ggml)
set(COMMON_DIR ${LLAMA_DIR}/common)
set(VENDOR_DIR ${LLAMA_DIR}/vendor)

# Try to find Vulkan for GPU support
find_package(Vulkan QUIET COMPONENTS glslc)

# On Mac building for Android, we need to point to the Vulkan SDK or Homebrew for C++ headers (vulkan.hpp)
if (APPLE AND NOT Vulkan_FOUND)
    set(Vulkan_INCLUDE_DIRS "/opt/homebrew/include")
    set(Vulkan_FOUND TRUE) # Force it if we know where headers are, but we still need the library for Android
endif()

set(VULKAN_GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/vulkan-generated")
set(VULKAN_GENERATED_HEADER "${VULKAN_GENERATED_DIR}/ggml-vulkan-shaders.hpp")
set(VULKAN_SHADER_GEN_BIN "${GGML_DIR}/src/ggml-vulkan/vulkan-shaders/build/vulkan-shaders-gen")
set(VULKAN_SHADER_SRC_DIR "${GGML_DIR}/src/ggml-vulkan/vulkan-shaders")
set(VULKAN_GENERATED_SOURCES "")

if (Vulkan_FOUND AND Vulkan_GLSLC_EXECUTABLE)
    if (EXISTS "${VULKAN_SHADER_GEN_BIN}")
        file(MAKE_DIRECTORY "${VULKAN_GENERATED_DIR}")
        file(MAKE_DIRECTORY "${VULKAN_GENERATED_DIR}/spv")
        file(GLOB VULKAN_SHADER_FILES CONFIGURE_DEPENDS "${VULKAN_SHADER_SRC_DIR}/*.comp")

        add_custom_command(
            OUTPUT "${VULKAN_GENERATED_HEADER}"
            COMMAND "${VULKAN_SHADER_GEN_BIN}"
                --output-dir "${VULKAN_GENERATED_DIR}/spv"
                --target-hpp "${VULKAN_GENERATED_HEADER}"
            DEPENDS ${VULKAN_SHADER_FILES} "${VULKAN_SHADER_GEN_BIN}"
            VERBATIM
        )
        list(APPEND VULKAN_GENERATED_SOURCES "${VULKAN_GENERATED_HEADER}")

        foreach(shader_file IN LISTS VULKAN_SHADER_FILES)
            get_filename_component(shader_name "${shader_file}" NAME_WE)
            set(vulkan_cpp_file "${VULKAN_GENERATED_DIR}/${shader_name}.cpp")
            add_custom_command(
                OUTPUT "${vulkan_cpp_file}"
                COMMAND "${VULKAN_SHADER_GEN_BIN}"
                    --glslc "${Vulkan_GLSLC_EXECUTABLE}"
                    --source "${shader_file}"
                    --output-dir "${VULKAN_GENERATED_DIR}/spv"
                    --target-hpp "${VULKAN_GENERATED_HEADER}"
                    --target-cpp "${vulkan_cpp_file}"
                DEPENDS "${shader_file}" "${VULKAN_GENERATED_HEADER}" "${VULKAN_SHADER_GEN_BIN}"
                VERBATIM
            )
            list(APPEND VULKAN_GENERATED_SOURCES "${vulkan_cpp_file}")
        endforeach()

        message(STATUS "Vulkan found and shader bundle generated, enabling GPU support")
        add_definitions(-DGGML_USE_VULKAN)
        set(VULKAN_SOURCES ${GGML_DIR}/src/ggml-vulkan/ggml-vulkan.cpp ${VULKAN_GENERATED_SOURCES})
    else()
        message(WARNING "Vulkan SDK found but vulkan-shaders-gen is missing. Run the shader generator before building GPU support.")
        set(VULKAN_SOURCES "")
    endif()
else()
    if (NOT Vulkan_FOUND)
        message(WARNING "Vulkan SDK not found. Install it to enable GPU support.")
    else()
        message(WARNING "Vulkan SDK is available but glslc is missing. GPU support will be disabled.")
    endif()
    message(STATUS "GPU support will be disabled (CPU only)")
endif()

set(ANDROID_CHAT
        ${GGML_DIR}/src/ggml-alloc.c
        ${GGML_DIR}/src/ggml-backend.cpp
        ${GGML_DIR}/src/ggml-backend-meta.cpp
        ${GGML_DIR}/src/ggml-threading.cpp
        ${GGML_DIR}/src/ggml-quants.c
        ${GGML_DIR}/src/ggml-backend-reg.cpp
        ${GGML_DIR}/src/ggml-backend-dl.cpp
        ${GGML_DIR}/src/ggml-opt.cpp
        ${GGML_DIR}/src/ggml-cpu/ops.cpp
        ${GGML_DIR}/src/ggml-cpu/vec.cpp
        ${GGML_DIR}/src/ggml-cpu/quants.c
        ${GGML_DIR}/src/ggml-cpu/traits.cpp
        ${GGML_DIR}/src/ggml-cpu/unary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/binary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
        ${GGML_DIR}/src/ggml-cpu/iqp.cpp
        ${GGML_DIR}/src/ggml.c
        ${GGML_DIR}/src/gguf.cpp
        ${VULKAN_SOURCES}

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
        ${LLAMA_DIR}/src/llama-memory-hybrid-iswa.cpp
        ${LLAMA_DIR}/src/llama-memory-hybrid-idx.cpp
        ${LLAMA_DIR}/src/llama-mmap.cpp
        ${LLAMA_DIR}/src/llama-hparams.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-iswa.cpp
        ${LLAMA_DIR}/src/llama-kv-cache.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-msa.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-dsa.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-dsv4.cpp
        ${LLAMA_DIR}/src/llama-kv-cache-dsa-iswa.cpp
        ${LLAMA_DIR}/src/llama-batch.cpp
        ${LLAMA_DIR}/src/llama-arch.cpp
        ${LLAMA_DIR}/src/llama-adapter.cpp
        ${LLAMA_DIR}/src/llama-chat.cpp
        ${LLAMA_DIR}/src/llama-graph.cpp
        ${LLAMA_DIR}/src/unicode.cpp
        ${LLAMA_DIR}/src/unicode-data.cpp

        ${VENDOR_DIR}/nlohmann/json_fwd.hpp
        ${VENDOR_DIR}/nlohmann/json.hpp

        ${COMMON_DIR}/arg.cpp
        ${COMMON_DIR}/base64.hpp
        ${COMMON_DIR}/common.cpp
        ${COMMON_DIR}/console.cpp
        ${COMMON_DIR}/json-schema-to-grammar.cpp
        ${COMMON_DIR}/log.cpp
        ${COMMON_DIR}/ngram-cache.cpp
        ${COMMON_DIR}/sampling.cpp

        ${LLAMA_DIR}/tools/mtmd/mtmd.cpp
        ${LLAMA_DIR}/tools/mtmd/mtmd-helper.cpp
        ${LLAMA_DIR}/tools/mtmd/mtmd-helper-gen.cpp
        ${LLAMA_DIR}/tools/mtmd/mtmd-audio.cpp
        ${LLAMA_DIR}/tools/mtmd/mtmd-image.cpp
        ${LLAMA_DIR}/tools/mtmd/clip.cpp
        ${LLAMA_DIR}/tools/mtmd/deprecation-warning.cpp

        ${LLAMA_DIR}/vendor/hash/hash.cpp
        ${LLAMA_DIR}/vendor/hash/sha256/sha256.c
        ${LLAMA_DIR}/vendor/hash/sha1/sha1.c
        ${LLAMA_DIR}/vendor/hash/xxhash/xxhash.c

        LLMInference.cpp
        ragengine/rag.cpp
        ragengine/rag_android.cpp
        llama_android.cpp
)

set(GGUF_READER_SOURCES
        ${GGML_DIR}/src/ggml.c
        ${GGML_DIR}/src/ggml-alloc.c
        ${GGML_DIR}/src/ggml-backend.cpp
        ${GGML_DIR}/src/ggml-backend-meta.cpp
        ${GGML_DIR}/src/ggml-threading.cpp
        ${GGML_DIR}/src/ggml-quants.c
        ${GGML_DIR}/src/ggml-backend-reg.cpp
        ${GGML_DIR}/src/ggml-backend-dl.cpp
        ${GGML_DIR}/src/ggml-opt.cpp
        ${GGML_DIR}/src/ggml-cpu/ops.cpp
        ${GGML_DIR}/src/ggml-cpu/vec.cpp
        ${GGML_DIR}/src/ggml-cpu/quants.c
        ${GGML_DIR}/src/ggml-cpu/traits.cpp
        ${GGML_DIR}/src/ggml-cpu/unary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/binary-ops.cpp
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
        ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
        ${GGML_DIR}/src/ggml-cpu/iqp.cpp
        ${GGML_DIR}/src/gguf.cpp
        GGUFReader.cpp
)

# Add all model builder files from src/models/
file(GLOB MODEL_FILES "${LLAMA_DIR}/src/models/*.cpp")
if(MODEL_FILES)
    list(APPEND ANDROID_CHAT ${MODEL_FILES})
    message(STATUS "Found model files in src/models/")
else()
    message(WARNING "No model files found in ${LLAMA_DIR}/src/models/")
endif()

# Add all mtmd model files from tools/mtmd/models/
file(GLOB MTMD_MODEL_FILES "${LLAMA_DIR}/tools/mtmd/models/*.cpp")
if(MTMD_MODEL_FILES)
    list(APPEND ANDROID_CHAT ${MTMD_MODEL_FILES})
    message(STATUS "Found mtmd model files in tools/mtmd/models/")
else()
    message(WARNING "No mtmd model files found in ${LLAMA_DIR}/tools/mtmd/models/")
endif()

set_source_files_properties(${LLAMA_DIR}/vendor/hash/sha1/sha1.c PROPERTIES LANGUAGE CXX)

configure_file(${GGML_DIR}/src/ggml-version.h.in ${CMAKE_CURRENT_BINARY_DIR}/ggml-version.h @ONLY)
configure_file(${LLAMA_DIR}/src/llama-version.h.in ${CMAKE_CURRENT_BINARY_DIR}/llama-version.h @ONLY)

add_compile_options("-ffile-prefix-map=${LLAMA_DIR}=.")
add_link_options("LINKER:--build-id=none")

function(build_library target_name)
    add_library(${target_name} SHARED ${ANDROID_CHAT})

    target_include_directories(${target_name}
            PUBLIC
            ${CMAKE_CURRENT_BINARY_DIR}
            ${COMMON_DIR}
            ${GGML_DIR}/include
            ${GGML_DIR}/src
            ${GGML_DIR}/src/ggml-cpu
            ${LLAMA_DIR}/include
            ${LLAMA_DIR}/src
            ${LLAMA_DIR}/src/models
            ${VENDOR_DIR}
            ${VENDOR_DIR}/hash
            ${LLAMA_DIR}/tools/mtmd
    )

    target_compile_definitions(${target_name} PRIVATE GGML_COMMIT="" GGML_VERSION="")

    target_compile_options(${target_name} PUBLIC
            -fvisibility=hidden -fvisibility-inlines-hidden
            -ffunction-sections -fdata-sections
    )

    if (Vulkan_FOUND)
        target_include_directories(${target_name} PRIVATE "/opt/homebrew/include" "${VULKAN_GENERATED_DIR}" "${GGML_DIR}/src/ggml-vulkan")
        target_link_libraries(${target_name} vulkan)
    endif()

    target_link_libraries(${target_name} android log dl atomic)

    target_link_options(${target_name} PRIVATE
            -Wl,--gc-sections -flto
            -Wl,--exclude-libs,ALL
            -Wl,-z,max-page-size=16384
    )
endfunction()
function(build_library_arm64 target_name cpu_flags)
    build_library(${target_name})
    target_sources(${target_name} PRIVATE ${GGML_DIR}/src/ggml-cpu/arch/arm/quants.c)
    target_compile_options(${target_name} PUBLIC
            -DGGML_USE_CPU -DGGML_USE_CPU_AARCH64 ${cpu_flags} -O3
    )
endfunction()

function(build_library_armv7a target_name cpu_flags fpu fpu_abi)
    build_library(${target_name})
    target_sources(${target_name} PRIVATE ${GGML_DIR}/src/ggml-cpu/arch/arm/quants.c)
    target_compile_options(${target_name} PUBLIC
            -DGGML_USE_CPU ${cpu_flags} ${fpu} ${fpu_abi} -O3
    )
endfunction()

function(build_library_universal target_name)
    build_library(${target_name})
    target_compile_options(${target_name} PUBLIC -DGGML_USE_CPU -DGGML_CPU_GENERIC -O3)
endfunction()

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

# GGUF reader
set(TARGET_NAME_GGUF_READER ggufreader)
add_library(${TARGET_NAME_GGUF_READER} SHARED ${GGUF_READER_SOURCES})
target_include_directories(${TARGET_NAME_GGUF_READER} PUBLIC
        ${CMAKE_CURRENT_BINARY_DIR} ${GGML_DIR}/include ${GGML_DIR}/src ${GGML_DIR}/src/ggml-cpu
)
target_compile_definitions(${TARGET_NAME_GGUF_READER} PRIVATE GGML_COMMIT="" GGML_VERSION="")
target_compile_options(${TARGET_NAME_GGUF_READER} PUBLIC
        -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections
)
target_link_options(${TARGET_NAME_GGUF_READER} PRIVATE
        -Wl,--gc-sections -flto -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384
)