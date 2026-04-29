cmake_minimum_required(VERSION 3.22.1)
project("render-engine" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# FFmpeg prebuilt libraries
set(FFMPEG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../ffmpeg_library/android)
set(FFMPEG_ABI ${CMAKE_ANDROID_ARCH_ABI})

foreach(LIB avcodec avformat avutil swresample swscale)
    add_library(${LIB} SHARED IMPORTED)
    set_target_properties(${LIB} PROPERTIES
        IMPORTED_LOCATION ${FFMPEG_DIR}/libs/${FFMPEG_ABI}/lib${LIB}.so)
endforeach()

include_directories(${FFMPEG_DIR}/${FFMPEG_ABI}/include)

# Source files
set(SOURCES
    src/main/cpp/render_engine_jni.cpp
    src/main/cpp/gl/EglCore.cpp
    src/main/cpp/gl/ShaderProgram.cpp
    src/main/cpp/gl/FullscreenQuad.cpp
    src/main/cpp/gl/FBOPair.cpp
    src/main/cpp/decode/Demuxer.cpp
    src/main/cpp/decode/HwDecoder.cpp
    src/main/cpp/decode/SurfaceTextureHelper.cpp
    src/main/cpp/decode/AudioDecoder.cpp
    src/main/cpp/core/SourceNode.cpp
    src/main/cpp/core/OutputNode.cpp
    src/main/cpp/audio/AudioOutput.cpp
    src/main/cpp/engine/RenderEngine.cpp
    src/main/cpp/engine/Timeline.cpp
)

add_library(render-engine SHARED ${SOURCES})

target_include_directories(render-engine PRIVATE
    src/main/cpp
    src/main/cpp/common
    src/main/cpp/gl
    src/main/cpp/decode
    src/main/cpp/core
    src/main/cpp/audio
    src/main/cpp/engine
)

# NDK system libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles3-lib GLESv3)
find_library(mediandk-lib mediandk)

target_link_libraries(render-engine
    avcodec avformat avutil swresample swscale
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles3-lib}
    ${mediandk-lib}
    aaudio
)
