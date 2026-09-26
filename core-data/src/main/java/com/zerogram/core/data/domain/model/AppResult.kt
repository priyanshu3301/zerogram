package com.zerogram.domain.model

/**
 * Generic result wrapper used across all domain layers.
 * Replaces Kotlin stdlib Result to carry typed [AppError] failures.
 */
sealed class AppResult<out T> {

    data class Success<out T>(val data: T) : AppResult<T>()

    data class Failure(val error: AppError) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorOrNull(): AppError? = (this as? Failure)?.error

    inline fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onFailure(block: (AppError) -> Unit): AppResult<T> {
        if (this is Failure) block(error)
        return this
    }

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }
}

/** Convenience: wrap a suspend block and convert exceptions to [AppResult.Failure]. */
suspend fun <T> runCatchingResult(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: Exception) {
    AppResult.Failure(AppError.UnknownError(e.message ?: "Unknown error", e))
}
