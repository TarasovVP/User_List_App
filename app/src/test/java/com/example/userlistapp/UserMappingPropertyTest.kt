package com.example.userlistapp

import com.example.userlistapp.data.remote.AddressDto
import com.example.userlistapp.data.remote.CompanyDto
import com.example.userlistapp.data.remote.UserDto
import com.example.userlistapp.data.repository.toEntity
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
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
    }
}
