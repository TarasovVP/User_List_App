package com.example.userlistapp

import com.example.userlistapp.feature.account.AccountImplementation
import com.example.userlistapp.feature.account.AccountImplementationFlag
import com.example.userlistapp.feature.account.AccountImplementationSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountImplementationSelectorTest {
    @Test
    fun `false selects unchanged legacy implementation`() {
        val selector = AccountImplementationSelector(flag(false))

        assertEquals(AccountImplementation.LEGACY, selector.selected())
    }

    @Test
    fun `true selects modular implementation`() {
        val selector = AccountImplementationSelector(flag(true))

        assertEquals(AccountImplementation.MODULAR, selector.selected())
    }

    private fun flag(modular: Boolean) = object : AccountImplementationFlag {
        override val useModularAccount = modular
    }
}
