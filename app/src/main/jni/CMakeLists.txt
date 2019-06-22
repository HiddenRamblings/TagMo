# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99")

include_directories( include )
add_library( # Specifies the name of the library.
             amiitool

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             amiibo.c
             drbg.c
             keygen.c
             main.c
             util.c
             version.c
             mbedtls/aes.c
             mbedtls/md.c
             mbedtls/md_wrap.c
             mbedtls/sha256.c

           )

target_link_libraries( # Specifies the target library.
                       amiitool
                      )
