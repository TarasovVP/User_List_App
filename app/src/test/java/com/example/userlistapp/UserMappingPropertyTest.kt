package com.example.userlistapp

import com.example.userlistapp.data.local.UserWithLocal
import com.example.userlistapp.data.remote.AddressDto
import com.example.userlistapp.data.remote.CompanyDto
import com.example.userlistapp.data.remote.UserDto
import com.example.userlistapp.data.repository.toDomain
import com.example.userlistapp.data.repository.toEntity
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMappingPropertyTest {
    @Test
    fun `mapping preserves every remote user field`() = runTest {
        checkAll(userDtos) { dto ->
            val entity = dto.toEntity()

            assertEquals(dto.id, entity.id)
            assertEquals(dto.firstName, entity.firstName)
            assertEquals(dto.lastName, entity.lastName)
            assertEquals(dto.age, entity.age)
            assertEquals(dto.email, entity.email)
            assertEquals(dto.phone, entity.phone)
            assertEquals(dto.username, entity.username)
            assertEquals(dto.image, entity.imageUrl)
            assertEquals(dto.role, entity.role)
            assertEquals(dto.company.name, entity.companyName)
            assertEquals(dto.company.department, entity.department)
            assertEquals(dto.company.title, entity.jobTitle)
            assertEquals(dto.address.address, entity.street)
            assertEquals(dto.address.city, entity.city)
            assertEquals(dto.address.state, entity.state)
            assertEquals(dto.address.country, entity.country)
        }
    }

    @Test
    fun `local mapping preserves every field and derives favorite from its timestamp`() = runTest {
        checkAll(usersWithLocal) { row ->
            val user = row.toDomain()

            assertEquals(row.id, user.id)
            assertEquals(row.firstName, user.firstName)
            assertEquals(row.lastName, user.lastName)
            assertEquals(row.age, user.age)
            assertEquals(row.email, user.email)
            assertEquals(row.phone, user.phone)
            assertEquals(row.username, user.username)
            assertEquals(row.imageUrl, user.imageUrl)
            assertEquals(row.role, user.role)
            assertEquals(row.companyName, user.companyName)
            assertEquals(row.department, user.department)
            assertEquals(row.jobTitle, user.jobTitle)
            assertEquals(row.street, user.street)
            assertEquals(row.city, user.city)
            assertEquals(row.state, user.state)
            assertEquals(row.country, user.country)
            assertEquals(row.favoriteCreatedAt != null, user.isFavorite)
            assertEquals(row.note, user.note)
            assertEquals(row.noteModifiedAt, user.noteModifiedAt)
        }
    }

    private companion object {
        val text = Arb.string(0..80)
        val userDtos = arbitrary {
            UserDto(
                id = Arb.int().bind(),
                firstName = text.bind(),
                lastName = text.bind(),
                age = Arb.int(min = 0, max = 150).bind(),
                email = text.bind(),
                phone = text.bind(),
                username = text.bind(),
                image = text.bind(),
                role = text.bind(),
                company = CompanyDto(
                    name = text.bind(),
                    department = text.bind(),
                    title = text.bind(),
                ),
                address = AddressDto(
                    address = text.bind(),
                    city = text.bind(),
                    state = text.bind(),
                    country = text.bind(),
                ),
            )
        }
        val usersWithLocal = arbitrary {
            UserWithLocal(
                id = Arb.int().bind(),
                firstName = text.bind(),
                lastName = text.bind(),
                age = Arb.int(min = 0, max = 150).bind(),
                email = text.bind(),
                phone = text.bind(),
                username = text.bind(),
                imageUrl = text.bind(),
                role = text.bind(),
                companyName = text.bind(),
                department = text.bind(),
                jobTitle = text.bind(),
                street = text.bind(),
                city = text.bind(),
                state = text.bind(),
                country = text.bind(),
                snapshotBatchId = Arb.long().bind(),
                favoriteCreatedAt = Arb.long().bind().takeIf { Arb.boolean().bind() },
                note = text.bind().takeIf { Arb.boolean().bind() },
                noteModifiedAt = Arb.long().bind().takeIf { Arb.boolean().bind() },
            )
        }
    }
}
