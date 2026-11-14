package com.jestk.browser.engine.adblock

/** Bit flags returned by the native `checkRequest`; `0` means allow. */
@JvmInline
value class MatchResult(val flags: Int) {
    val shouldBlock: Boolean get() = flags and BLOCK != 0
    val isImportant: Boolean get() = flags and IMPORTANT != 0
    val hasException: Boolean get() = flags and EXCEPTION != 0
    val hasRedirect: Boolean get() = flags and REDIRECT != 0
    val matched: Boolean get() = flags and MATCHED != 0

    companion object {
        const val BLOCK = 1
        const val IMPORTANT = 2
        const val EXCEPTION = 4
        const val REDIRECT = 8
        const val MATCHED = 16

        val ALLOW = MatchResult(0)
    }
}
