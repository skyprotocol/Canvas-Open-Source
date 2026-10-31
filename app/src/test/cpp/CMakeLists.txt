cmake_minimum_required(VERSION 3.18)

project(canvas_native_tests CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

enable_testing()

find_package(Threads REQUIRED)
add_executable(initialization_gate_test InitializationGateTest.cpp)
target_include_directories(initialization_gate_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp/Core)
target_compile_options(initialization_gate_test PRIVATE -Wall -Wextra -Werror -UNDEBUG)
target_link_libraries(initialization_gate_test PRIVATE Threads::Threads)
add_test(NAME initialization_gate_test COMMAND initialization_gate_test)

add_executable(chat_flag_fix_test ChatFlagFixTest.cpp)
target_include_directories(chat_flag_fix_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp/Core)
target_compile_options(chat_flag_fix_test PRIVATE -Wall -Wextra -Werror)

add_test(NAME chat_flag_fix_test COMMAND chat_flag_fix_test)

add_executable(other_audio_test OtherAudioTest.cpp)
target_include_directories(other_audio_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp/Core)
target_compile_options(other_audio_test PRIVATE -Wall -Wextra -Werror)

add_test(NAME other_audio_test COMMAND other_audio_test)

add_executable(other_audio_swap_test OtherAudioSwapTest.cpp)
target_include_directories(other_audio_swap_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp/Core)
target_compile_options(other_audio_swap_test PRIVATE -Wall -Wextra -Werror)

add_test(NAME other_audio_swap_test COMMAND other_audio_swap_test)
