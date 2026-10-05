package com.zerogram.navigation

object NavigationRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val TRANSFERS = "transfers"
    const val RECENTLY_DELETED = "recently_deleted"
    
    // Use string formatting to pass arguments if needed, or stick to simple constants
    const val CATEGORY_PREFIX = "category/"
    fun category(name: String) = "$CATEGORY_PREFIX$name"
    
    const val VAULT_UNLOCK = "vault_unlock"
    
    const val VAULT_SELECTION = "vault_selection"
    const val VAULT_CREATE = "vault_create"
    
    const val FOLDER_PREFIX = "folder" // Base route
    fun folder(id: String?) = if (id.isNullOrEmpty()) FOLDER_PREFIX else "$FOLDER_PREFIX?id=$id"
    // In NavGraph we'll use "folder?id={id}"
    const val FOLDER_ROUTE_PATTERN = "folder?id={id}"
}