package io.github.mabrur.streamly.domain.model

@JvmInline
value class Category(val name: String) {
    companion object {
        val All = Category("All")
    }
}
