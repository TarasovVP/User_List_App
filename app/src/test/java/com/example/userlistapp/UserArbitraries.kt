package com.example.userlistapp

import com.example.userlistapp.domain.model.User
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map

internal object UserArbitraries {
    private val word = Arb.int(0..1_000_000).map { value -> "word$value" }

    val user: Arb<User> = arbitrary {
        val id = Arb.int().bind()
        val firstName = word.bind()
        val lastName = word.bind()
        User(
            id = id,
            firstName = firstName,
            lastName = lastName,
            age = Arb.int(0..130).bind(),
            email = "${word.bind()}@example.test",
            phone = "+${Arb.int(1_000_000..9_999_999).bind()}",
            username = word.bind(),
            imageUrl = "https://example.test/$id.png",
            role = word.bind(),
            companyName = word.bind(),
            department = word.bind(),
            jobTitle = word.bind(),
            street = word.bind(),
            city = word.bind(),
            state = word.bind(),
            country = word.bind(),
            isFavorite = Arb.boolean().bind(),
            note = null,
            noteModifiedAt = null,
            viewedAt = if (Arb.boolean().bind()) Arb.int(0..1_000_000).bind().toLong() else null,
        )
    }
}
