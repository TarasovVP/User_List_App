package com.example.userlistapp

import com.example.userlistapp.core.common.AppError
import com.example.userlistapp.core.common.toUiText
import com.example.userlistapp.data.remote.AddressDto
import com.example.userlistapp.data.remote.CompanyDto
import com.example.userlistapp.data.remote.UserDto
import com.example.userlistapp.data.repository.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class UserModelAndMappingTest {
    @Test
    fun `full name and initials ignore missing name parts`() {
        val user = sampleUser(firstName = "Ada", lastName = "Lovelace")
        assertEquals("Ada Lovelace", user.fullName)
        assertEquals("AL", user.initials)
        assertEquals("1 Main, London, England, UK", user.fullAddress)
    }

    @Test
    fun `remote dto maps only required display fields`() {
        val dto = UserDto(
            7,
            "Ada",
            "Lovelace",
            36,
            "ada@example.com",
            "123",
            "ada",
            "image",
            "admin",
            CompanyDto("Analytical", "R&D", "Engineer"),
            AddressDto("1 Main", "London", "England", "UK")
        )
        val entity = dto.toEntity()
        assertEquals(7, entity.id)
        assertEquals("Analytical", entity.companyName)
        assertEquals("Engineer", entity.jobTitle)
    }

    @Test
    fun `HTTP error UI text preserves status code as formatting argument`() {
        val text = AppError.Http(503).toUiText()

        assertEquals(R.string.error_service, text.resourceId)
        assertEquals(listOf(503), text.args)
        assertEquals(text, AppError.Http(503).toUiText())
    }
}
