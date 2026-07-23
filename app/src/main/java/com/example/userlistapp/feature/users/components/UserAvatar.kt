package com.example.userlistapp.feature.users.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.userlistapp.R

@Composable
fun UserAvatar(url: String, description: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = description,
        placeholder = painterResource(R.drawable.ic_avatar_placeholder),
        error = painterResource(R.drawable.ic_avatar_placeholder),
        fallback = painterResource(R.drawable.ic_avatar_placeholder),
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
