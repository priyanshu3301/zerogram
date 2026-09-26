package com.example.zerogram.telegram

import com.example.zerogram.domain.model.AppError
import com.example.zerogram.domain.model.AppResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume

/**
 * Sends a query to TDLib and suspends until a response is received.
 * Maps TdApi.Error to [AppError] inside an [AppResult.Failure].
 * Successful responses are returned in [AppResult.Success].
 */
suspend inline fun <reified T : TdApi.Object> Client.sendSuspend(query: TdApi.Function<*>): AppResult<T> =
    suspendCancellableCoroutine { continuation ->
        this.send(query, { result ->
            when (result) {
                is TdApi.Error -> {
                    val appError = if (result.code == 420) {
                        // FLOOD_WAIT — extract the retry delay in seconds from the message
                        val waitSeconds = Regex("""(\d+)""").find(result.message)?.groupValues?.get(1)?.toIntOrNull() ?: 30
                        AppError.RateLimitError("Rate limited by Telegram", waitSeconds)
                    } else {
                        AppError.UnknownError("TDLib Error ${result.code}: ${result.message}")
                    }
                    continuation.resume(AppResult.Failure(appError))
                }
                is T -> {
                    continuation.resume(AppResult.Success(result))
                }
                else -> {
                    continuation.resume(
                        AppResult.Failure(
                            AppError.UnknownError("Unexpected TDLib response type: ${result.javaClass.simpleName}")
                        )
                    )
                }
            }
        }, { exception ->
            continuation.resume(
                AppResult.Failure(
                    AppError.UnknownError("TDLib execution failed: ${exception.message}")
                )
            )
        })
    }
