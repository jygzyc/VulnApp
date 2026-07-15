cmake_minimum_required(VERSION 3.22.1)
project(mochat CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Force position-independent code for the shared library.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_library(mochat SHARED
    mochat.cpp
)

# Hide all symbols by default; only JNI exports remain visible.
# In the HARD flavor we additionally pass -O2 and enable string obfuscation via
# the OBFUSCATE macro (compile-time). EASY flavor passes -O0 and OBFUSCATE is a no-op.
target_compile_options(mochat PRIVATE
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
)
target_link_options(mochat PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)

# NDK logcat.
find_library(log-lib log)
target_link_libraries(mochat ${log-lib})
