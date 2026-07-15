// MoChat native obfuscation helpers.
//
// OBFUSCATE() produces a compile-time XOR-encrypted string literal whose cleartext
// does NOT appear in the .rodata section of libmochat.so. Decryption happens lazily
// at first access into a thread-local buffer.
//
// In the EASY flavor you can #define MOCHAT_EASY before including this header to
// make OBFUSCATE a no-op (cleartext), keeping the native lib readable for learners.

#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

namespace mochat {

constexpr uint8_t KEY = 0x5A;  // 'Z' — single-byte compile-time key.

constexpr size_t cstrlen(const char* s) {
    size_t n = 0;
    while (s[n]) ++n;
    return n;
}

// Compile-time XOR of a single position.
constexpr char xcode(char c, size_t i) {
    return static_cast<char>(static_cast<uint8_t>(c) ^ (KEY + static_cast<uint8_t>(i & 0x1f)));
}

// Encrypted literal holder. The ciphertext array is a compile-time constant built
// with a constexpr constructor — no C++23 in-function statics.
template <size_t N>
struct ObfString {
    char data[N];
    constexpr ObfString(const char (&s)[N]) : data{} {
        for (size_t i = 0; i < N; ++i) data[i] = xcode(s[i], i);
    }
};

// Runtime-decode into a thread-local stack buffer (max 512 chars incl. NUL).
inline const char* deobf(const char* enc, size_t n) {
    static thread_local char buf[512];
    if (n >= sizeof(buf)) n = sizeof(buf) - 1;
    for (size_t i = 0; i < n; ++i) {
        buf[i] = static_cast<char>(static_cast<uint8_t>(enc[i]) ^ (KEY + static_cast<uint8_t>(i & 0x1f)));
    }
    buf[n] = '\0';
    return buf;
}

}  // namespace mochat

#ifdef MOCHAT_EASY
// Easy flavor: OBFUSCATE returns the cleartext directly.
#define OBFUSCATE(s) (s)
#else
// Hard flavor: produce an encrypted compile-time constant, decrypt at access.
// Uses a file-scope constexpr instance inside an IIFE (valid C++17 — the static
// local is in a non-constexpr lambda).
#define OBFUSCATE(s) \
    (::mochat::deobf( \
        MOCHAT_OBF_INSTANCE(s).data, \
        ::mochat::cstrlen(s) + 1))

#define MOCHAT_OBF_INSTANCE(s) \
    ([]() -> const ::mochat::ObfString<sizeof(s)>& { \
        static const ::mochat::ObfString<sizeof(s)> inst{s}; \
        return inst; \
    }())
#endif
