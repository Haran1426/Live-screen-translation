cmake_minimum_required(VERSION 3.22.1)
project(screenlingo_privacy LANGUAGES CXX)

add_library(screenlingo_privacy SHARED log_privacy.cpp)
target_compile_features(screenlingo_privacy PRIVATE cxx_std_17)
target_compile_options(screenlingo_privacy PRIVATE -Wall -Wextra -Werror)
target_link_libraries(screenlingo_privacy PRIVATE log)
