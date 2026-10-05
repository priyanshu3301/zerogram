package com.zerogram.core.ui.components

data class SelectionDetails(
    val title: String,
    val isMultiple: Boolean,
    val name: String? = null,
    val dateModified: String? = null,
    val sizeText: String,
    val location: String? = null,
    val itemsText: String? = null
)
