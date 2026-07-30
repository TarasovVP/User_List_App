package com.example.userlistapp

import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.domain.usecase.FilterAndSortUsersUseCase
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterAndSortUsersPropertyTest {
    private val filter = FilterAndSortUsersUseCase()
    private val users = Arb.list(UserArbitraries.user, 0..40)

    @Test
    fun `empty input always produces empty output`() = runTest {
        checkAll(Arb.string(0..80), Arb.boolean()) { query, favoritesOnly ->
            assertTrue(
                filter(
                    users = emptyList(),
                    query = query,
                    sort = UserSort.NAME_ASCENDING,
                    favoritesOnly = favoritesOnly,
                ).isEmpty()
            )
        }
    }

    @Test
    fun `blank query preserves every user and requested name order`() = runTest {
        checkAll(users) { input ->
            UserSort.entries.forEach { sort ->
                val result = filter(input, " \t\n ", sort, favoritesOnly = false)
                assertEquals(input.size, result.size)
                assertEquals(input.multiset(), result.multiset())
                assertSorted(result, sort)
            }
        }
    }

    @Test
    fun `favorites selection returns exactly favorites`() = runTest {
        checkAll(users) { input ->
            val result = filter(
                input,
                query = "",
                sort = UserSort.NAME_ASCENDING,
                favoritesOnly = true,
            )

            assertTrue(result.all(User::isFavorite))
            assertEquals(input.filter(User::isFavorite).multiset(), result.multiset())
        }
    }

    @Test
    fun `search is case insensitive for every searchable field`() = runTest {
        checkAll(UserArbitraries.user) { user ->
            listOf(user.fullName, user.email, user.companyName).forEach { field ->
                val upper = filter(listOf(user), field.uppercase(), UserSort.NAME_ASCENDING, false)
                val lower = filter(listOf(user), field.lowercase(), UserSort.NAME_ASCENDING, false)
                assertEquals(listOf(user), upper)
                assertEquals(upper, lower)
            }
        }
    }

    @Test
    fun `sorting is monotonic in both directions`() = runTest {
        checkAll(users) { input ->
            UserSort.entries.forEach { sort ->
                assertSorted(filter(input, "", sort, favoritesOnly = false), sort)
            }
        }
    }

    @Test
    fun `combined filtering matches the reference invariants`() = runTest {
        checkAll(users, Arb.boolean()) { input, favoritesOnly ->
            val query = input.firstOrNull()?.email?.substringBefore('@').orEmpty()
            UserSort.entries.forEach { sort ->
                val actual = filter(input, query, sort, favoritesOnly)
                val expected = input
                    .filter { !favoritesOnly || it.isFavorite }
                    .filter { user ->
                        query.isBlank() || listOf(user.fullName, user.email, user.companyName)
                            .any { it.contains(query, ignoreCase = true) }
                    }
                    .sortedBy { it.fullName.lowercase() }
                    .let { if (sort == UserSort.NAME_ASCENDING) it else it.reversed() }
                assertEquals(expected, actual)
            }
        }
    }

    @Test
    fun `multi-word terms may match different fields but every term is required`() = runTest {
        checkAll(UserArbitraries.user) { user ->
            val matchingQuery = "${user.firstName.uppercase()} ${user.companyName.lowercase()}"
            assertEquals(
                listOf(user),
                filter(listOf(user), matchingQuery, UserSort.NAME_ASCENDING, false),
            )
            assertTrue(
                filter(
                    listOf(user),
                    "$matchingQuery guaranteed-missing-term",
                    UserSort.NAME_ASCENDING,
                    false,
                ).isEmpty()
            )
        }
    }

    private fun assertSorted(result: List<User>, sort: UserSort) {
        val names = result.map { it.fullName.lowercase() }
        val expected = names.sorted().let {
            if (sort == UserSort.NAME_ASCENDING) it else it.reversed()
        }
        assertEquals(expected, names)
    }

    private fun List<User>.multiset() = groupingBy { it }.eachCount()
}
