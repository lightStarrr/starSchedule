package com.star.schedule.feature.importing.sleepdown.presentation

import com.star.schedule.R
import com.star.schedule.feature.importing.sharecode.presentation.ShareCodeImportBrand
import com.star.schedule.feature.importing.sharecode.presentation.ShareCodeImportError
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownProtocolException
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownServiceBusyException
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownShareRejectedException
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownUnsupportedPayloadException
import kotlinx.serialization.SerializationException
import java.io.IOException

fun sleepDownShareCodeBrand() = ShareCodeImportBrand(
    logoRes = R.drawable.ic_sleepdown_logo,
    colorScheme = { base -> sleepDownImportColorScheme(base) },
    hintRes = R.string.sleepdown_import_key_hint,
)

/**
 * SleepDown 链路抛出的异常 → 页面上的错误类别。
 */
fun sleepDownImportErrorOf(error: Throwable): ShareCodeImportError = when (error) {
    is IOException -> ShareCodeImportError.Network
    is SleepDownServiceBusyException -> ShareCodeImportError.Busy
    is SleepDownShareRejectedException -> ShareCodeImportError.Rejected
    is SleepDownProtocolException,
    is SleepDownUnsupportedPayloadException,
    is SerializationException,
    -> ShareCodeImportError.Unsupported
    else -> ShareCodeImportError.Rejected
}
