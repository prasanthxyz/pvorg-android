package com.pvorg.android.model

enum class TriState {
    YES,
    NO,
    UNDECIDED,
    ;

    val next: TriState
        get() = when (this) {
            YES -> NO
            NO -> UNDECIDED
            UNDECIDED -> YES
        }
}
