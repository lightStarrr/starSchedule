package com.star.schedule.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType

/**
 * 后端 JSON 的唯一入口。
 *
 * `ignoreUnknownKeys` 必须有：服务端加字段时老客户端不能直接崩。
 * 默认 [Json] 不会输出等于默认值的字段，所以可空字段用 `= null` 声明即可自动省略，
 * 不需要额外打开 `explicitNulls`。
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
}

val ApiJsonMediaType: MediaType = "application/json; charset=utf-8".toMediaType()
