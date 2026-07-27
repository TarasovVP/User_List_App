package com.example.userlistapp

import com.example.userlistapp.core.common.EMPTY

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
                String.EMPTY,
                UserSort.NAME_ASCENDING,
                favoritesOnly = false
            ).map { it.id },
        )
        assertEquals(
            listOf(2, 1),
            filterAndSortUsers(
                users,
                String.EMPTY,
                UserSort.NAME_DESCENDING,
                favoritesOnly = false
            ).map { it.id },
        )
    }

    @Test
    fun `sorting by recently viewed uses timestamp name and then id`() {
        val users = listOf(
            sampleUser(id = 1, firstName = "A", viewedAt = 100L),
            sampleUser(id = 2, firstName = "B", viewedAt = 200L),
            sampleUser(id = 3, firstName = "C", viewedAt = 200L),
            sampleUser(id = 4, firstName = "D", viewedAt = null),
            sampleUser(id = 5, firstName = "E", viewedAt = null),
        )

        val result = filterAndSortUsers(
            users,
            String.EMPTY,
            UserSort.RECENTLY_VIEWED,
            favoritesOnly = false
        )

        assertEquals(listOf(2, 3, 1, 4, 5), result.map { it.id })
    }

    @Test
    fun `sorting by recently viewed is case insensitive for name ties`() {
        val users = listOf(
            sampleUser(id = 2, firstName = "b", viewedAt = 200L),
            sampleUser(id = 1, firstName = "A", viewedAt = 200L),
        )

        val result = filterAndSortUsers(
            users,
            String.EMPTY,
            UserSort.RECENTLY_VIEWED,
            favoritesOnly = false
        )

        assertEquals(listOf(1, 2), result.map { it.id })
    }

    @Test
    fun `sorting by recently viewed uses id as final tie-breaker for case-only name differences`() {
        val users = listOf(
            sampleUser(id = 2, firstName = "alpha User", viewedAt = 100L),
            sampleUser(id = 1, firstName = "ALPHA User", viewedAt = 100L),
        )

        val result = filterAndSortUsers(
            users,
            String.EMPTY,
            UserSort.RECENTLY_VIEWED,
            favoritesOnly = false
        )

        assertEquals(listOf(1, 2), result.map { it.id })
    }

    @Test
    fun `sorting by recently viewed uses id tie-breaker for unviewed users`() {
        val users = listOf(
            sampleUser(id = 2, firstName = "A", viewedAt = null),
            sampleUser(id = 1, firstName = "A", viewedAt = null),
        )

        val result = filterAndSortUsers(
            users,
            String.EMPTY,
            UserSort.RECENTLY_VIEWED,
            favoritesOnly = false
        )

        assertEquals(listOf(1, 2), result.map { it.id })
    }

    @Test
    fun `sorting by recently viewed combines with favoritesOnly filter`() {
        val users = listOf(
            sampleUser(id = 1, firstName = "A", viewedAt = 200L, favorite = false),
            sampleUser(id = 2, firstName = "B", viewedAt = 100L, favorite = true),
        )

        val result = filterAndSortUsers(
            users,
            String.EMPTY,
            UserSort.RECENTLY_VIEWED,
            favoritesOnly = true
        )

        assertEquals(listOf(2), result.map { it.id })
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

    @Test
    fun `multi-word search allows terms to match different fields`() {
        val result = filterAndSortUsers(
            users = listOf(grace, ada),
            query = "  ADA   analytical  ",
            sort = UserSort.NAME_ASCENDING,
            favoritesOnly = false,
        )

        assertEquals(listOf(1), result.map { it.id })
    }

    @Test
    fun `multi-word search requires every term to match`() {
        assertTrue(search("Ada missing").isEmpty())
    }

    private fun search(query: String) = filterAndSortUsers(
        users = listOf(ada, grace),
        query = query,
        sort = UserSort.NAME_ASCENDING,
        favoritesOnly = false,
    )
}
