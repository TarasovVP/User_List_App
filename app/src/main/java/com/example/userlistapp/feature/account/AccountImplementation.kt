package com.example.userlistapp.feature.account

import com.example.userlistapp.BuildConfig
import javax.inject.Inject

interface AccountImplementationFlag {
    val useModularAccount: Boolean
}

class LocalAccountImplementationFlag @Inject constructor() : AccountImplementationFlag {
    override val useModularAccount: Boolean = BuildConfig.USE_MODULAR_ACCOUNT
}

enum class AccountImplementation {
    LEGACY,
    MODULAR,
}

class AccountImplementationSelector @Inject constructor(
    private val flag: AccountImplementationFlag,
) {
    fun selected(): AccountImplementation =
        if (flag.useModularAccount) AccountImplementation.MODULAR else AccountImplementation.LEGACY
}
