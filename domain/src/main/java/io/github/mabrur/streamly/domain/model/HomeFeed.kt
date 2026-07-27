package io.github.mabrur.streamly.domain.model

data class HomeFeed(
    val categories: List<Category>,
    val videos: List<Video>,
) {
    fun filteredBy(category: Category): HomeFeed =
        if (category == Category.All) this
        else copy(videos = videos.filter { it.category == category.name })
}
