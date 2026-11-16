package com.star.schedule.feature.importing.wakeup.presentation

import com.star.schedule.R
import com.star.schedule.feature.importing.sharecode.presentation.ShareCodeImportBrand
import com.star.schedule.feature.importing.sharecode.presentation.ShareCodeImportError
import com.star.schedule.feature.importing.wakeup.domain.WakeUpProtocolException
import kotlinx.serialization.SerializationException
import java.io.IOException

fun wakeUpShareCodeBrand() = ShareCodeImportBrand(
    logoRes = R.drawable.ic_wakeup_brand,
    colorScheme = { base -> wakeUpImportColorScheme(base) },
    hintRes = R.string.wakeup_import_key_hint,
)

/**
 * WakeUp 链路抛出的异常 → 页面上的错误类别。
 */
fun wakeUpImportErrorOf(error: Throwable): ShareCodeImportError = when (error) {
    // okhttp 连不上、超时都走 IOException。
    is IOException -> ShareCodeImportError.Network
    // 协议常量过期、签名被拒、密文解不开：跟用户输错口令无关，别让他反复重试。
    is WakeUpProtocolException -> ShareCodeImportError.Unsupported
    // 返回体不是预期的 JSON：WakeUp 改过分享格式，不是口令本身的问题。
    is SerializationException -> ShareCodeImportError.Unsupported
    // 服务端明确拒绝，或 HTTP 失败：口令无效/过期。
    else -> ShareCodeImportError.Rejected
}
