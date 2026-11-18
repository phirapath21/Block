package com.example.btcwidget.ui.social

// Search: people, notes, articles, and resolving what was typed.
// Extensions on SocialViewModel, kept apart from the class: they use only what it shares.

suspend fun SocialViewModel.resolve(query: String) = runCatching { repo.resolve(query) }.getOrNull()

suspend fun SocialViewModel.searchArticles(query: String) = runCatching { repo.searchArticles(query) }.getOrDefault(emptyList())

suspend fun SocialViewModel.recentArticles(following: Boolean, until: Long? = null) = runCatching { repo.recentArticles(following, until) }.getOrDefault(emptyList())

suspend fun SocialViewModel.searchPeople(query: String) = runCatching { repo.searchPeople(query) }.getOrDefault(emptyList())

suspend fun SocialViewModel.searchNotes(query: String) = runCatching { repo.searchNotes(query) }.getOrDefault(emptyList())
