package com.example.userlistapp

import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.domain.usecase.FilterAndSortUsersUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserListFilteringTest {
    private val filterAndSortUsers = FilterAndSortUsersUseCase()
    private val ada = sampleUser(id = 1, firstName = "Ada", lastName = "Lovelace", favorite = true)
    private val grace = sampleUser(id = 2, firstName = "Grace", lastName = "Hopper")

    @Test
    fun `search is case insensitive across name email and company`() {
        assertEquals(listOf(1), search("lOvElAcE").map { it.id })
        assertEquals(listOf(2), search("GRACE@EXAMPLE.COM").map { it.id })
        assertEquals(listOf(1, 2), search("aNaLyTiCaL").map { it.id })
    }

    @Test
    fun `search does not use fields outside the product contract`() {
        assertTrue(search("engineer").isEmpty())
        assertTrue(search("user").isEmpty())
    }

    @Test
    fun `sorting supports ascending and descending full name order`() {
        val users = listOf(grace, ada)

        assertEquals(
            listOf(1, 2),
            filterAndSortUsers(
                users,
                "",
                UserSort.NAME_ASCENDING,
                favoritesOnly = false
            ).map { it.id },
        )
        assertEquals(
            listOf(2, 1),
            filterAndSortUsers(
                users,
                "",
                UserSort.NAME_DESCENDING,
                favoritesOnly = false
            ).map { it.id },
        )
    }

    @Test
    fun `favorites filter combines with search and sorting`() {
        val result = filterAndSortUsers(
            users = listOf(grace, ada),
            query = "example.com",
            sort = UserSort.NAME_DESCENDING,
            favoritesOnly = true,
        )

        assertEquals(listOf(1), result.map { it.id })
    }

    private fun search(query: String) = filterAndSortUsers(
        users = listOf(ada, grace),
        query = query,
        sort = UserSort.NAME_ASCENDING,
        favoritesOnly = false,
    )
}
