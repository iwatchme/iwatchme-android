# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
project("jetpackstarterplayer_native")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

message("Hello: ${CMAKE_CURRENT_SOURCE_DIR}")
set(FFMPEG_ABI ${CMAKE_ANDROID_ARCH_ABI})
if(NOT FFMPEG_ABI)
    set(FFMPEG_ABI armeabi-v7a)
endif()

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../ffmpeg_library/android/libs/${FFMPEG_ABI}/libavcodec.so)

add_library(avfilter SHARED IMPORTED)
set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../ffmpeg_library/android/libs/${FFMPEG_ABI}/libavfilter.so)

add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../ffmpeg_library/android/libs/${FFMPEG_ABI}/libavformat.so)

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../ffmpeg_library/android/libs/${FFMPEG_ABI}/libavutil.so)

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../ffmpeg_library/android/libs/${FFMPEG_ABI}/libswresample.so)

add_library(swscale SHARED IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../ffmpeg_library/android/libs/${FFMPEG_ABI}/libswscale.so)




include_directories(../ffmpeg_library/android/${FFMPEG_ABI}/include)

include_directories(main/cpp/codec)
include_directories(main/cpp/common)

add_library( # Sets the name of the library.
        native-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        src/main/cpp/native-lib.cpp
        src/main/cpp/codec/FFmpegMediaCodec.cpp
        )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        native-lib

        avcodec
        avfilter
        avformat
        avutil
        swresample
        swscale

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})
