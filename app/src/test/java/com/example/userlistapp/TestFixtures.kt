package com.example.userlistapp

import com.example.userlistapp.domain.model.User

fun sampleUser(
    id: Int = 1,
    firstName: String = "Ada",
    lastName: String = "Lovelace",
    favorite: Boolean = false,
    note: String? = null,
) = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    age = 36,
    email = "${firstName.lowercase()}@example.com",
    phone = "123",
    username = firstName.lowercase(),
    imageUrl = "",
    role = "user",
    companyName = "Analytical",
    department = "R&D",
    jobTitle = "Engineer",
    street = "1 Main",
    city = "London",
    state = "England",
    country = "UK",
    isFavorite = favorite,
    note = note,
)
