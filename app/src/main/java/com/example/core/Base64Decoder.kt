package com.example.core

import android.util.Base64

object Base64Decoder {
    /**
     * AI 约束：实现 decode 方法时，必须先使用 Regex("\\s+") 移除所有空格和换行符，
     * 并根据 length % 4 自动在末尾补齐 =，然后再调用 android.util.Base64.decode(str, Base64.NO_WRAP)。
     */
    fun decode(input: String): String {
        var sanitized = input.replace(Regex("\\s+"), "")
        val remainder = sanitized.length % 4
        if (remainder != 0) {
            sanitized += "=".repeat(4 - remainder)
        }
        val bytes = Base64.decode(sanitized, Base64.NO_WRAP)
        return String(bytes, Charsets.UTF_8)
    }
}
