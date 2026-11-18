package com.star.schedule.feature.emoji.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.star.schedule.feature.datasource.domain.OptionalResourceRepository
import com.star.schedule.feature.datasource.domain.OptionalResourceState
import com.star.schedule.feature.emoji.data.EmojiCatalog
import com.star.schedule.feature.emoji.data.EmojiGroup
import com.star.schedule.feature.emoji.data.EmojiPickerRecentsRepository
import com.star.schedule.feature.emoji.data.EmojiTypefaceLoader
import com.star.schedule.feature.emoji.data.NotoEmojiFontResource
import com.star.schedule.feature.emoji.domain.EmojiPattern
import com.star.schedule.feature.emoji.domain.EMOJI_LAYOUT_SEED
import com.star.schedule.feature.emoji.domain.quantizedEmojiScale
import com.star.schedule.feature.emoji.domain.EmojiWallpaperColors
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec
import com.star.schedule.feature.emoji.domain.emojiWallpaperLayout
import com.star.schedule.feature.emoji.domain.emojiWallpaperPalettes
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 编辑页一次最多可选的表情数。 */
const val EMOJI_WALLPAPER_MAX_EMOJIS = 14

/** 表情选择器里「最近使用」的虚拟组 id，不会与资产里的组 id 冲突。 */
const val EMOJI_GROUP_RECENT = "recent"

sealed interface EmojiWallpaperEvent {
    /** 导出完成，把文件交给调用方打开预览页。 */
    data class Exported(val file: File) : EmojiWallpaperEvent
    data object ExportFailed : EmojiWallpaperEvent
    /** 已选表情达到上限，提示一下即可。 */
    data object PickerFull : EmojiWallpaperEvent
}

sealed interface EmojiWallpaperUiState {
    data object Loading : EmojiWallpaperUiState
    data object LoadFailed : EmojiWallpaperUiState

    data class Ready(
        /** 已选表情（1..[EMOJI_WALLPAPER_MAX_EMOJIS]）。 */
        val emojis: List<String>,
        val pattern: EmojiPattern,
        /** 0..1 */
        val scale: Float,
        val paletteIndex: Int,
        val palettes: List<EmojiWallpaperColors>,
        val seed: Long,
        val groups: List<EmojiGroup>,
        /** 字体画不出来的表情已被目录过滤掉。 */
        val typeface: Typeface,
        val pickerOpen: Boolean,
        /** 选择器当前页签：组 id 或 [EMOJI_GROUP_RECENT]。 */
        val pickerGroupId: String,
        val recents: List<String>,
        val exporting: Boolean,
    ) : EmojiWallpaperUiState {
        val spec: EmojiWallpaperSpec
            get() = EmojiWallpaperSpec(
                emojis = emojis,
                pattern = pattern,
                scale = scale,
                colors = palettes.getOrElse(paletteIndex) { palettes.first() },
                seed = seed,
            )
    }
}

/**
 * 表情壁纸编辑页：负责加载字体与表情目录、保存编辑中的
 * [EmojiWallpaperSpec]（拆成可序列化字段存 [SavedStateHandle]，旋转/进程重建不丢）、
 * 以及在 Default 上把当前组合导出成 PNG。
 */
class EmojiWallpaperViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val appContext: Context,
    private val optionalResources: OptionalResourceRepository,
    private val recentsRepository: EmojiPickerRecentsRepository,
    systemSeedArgb: Int?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val palettes = emojiWallpaperPalettes(systemSeedArgb)

    private val editing = MutableStateFlow(Editing())
    private val picker = MutableStateFlow(Picker())
    private val exporting = MutableStateFlow(false)

    val uiState: StateFlow<EmojiWallpaperUiState> = combine(
        editing,
        picker,
        exporting,
        recentsRepository.observe(),
    ) { editing, picker, exporting, recents ->
        when {
            editing.status != Status.Ready -> when (editing.status) {
                Status.Loading -> EmojiWallpaperUiState.Loading
                Status.Failed -> EmojiWallpaperUiState.LoadFailed
                Status.Ready -> error("unreachable")
            }
            else -> EmojiWallpaperUiState.Ready(
                emojis = editing.emojis,
                pattern = editing.pattern,
                scale = editing.scale,
                paletteIndex = editing.paletteIndex,
                palettes = palettes,
                seed = editing.seed,
                groups = editing.groups,
                typeface = editing.typeface!!,
                pickerOpen = picker.open,
                pickerGroupId = picker.groupId,
                recents = recents,
                exporting = exporting,
            )
        }
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        EmojiWallpaperUiState.Loading,
    )

    private val mutableEvents = MutableSharedFlow<EmojiWallpaperEvent>()
    val events: SharedFlow<EmojiWallpaperEvent> = mutableEvents.asSharedFlow()

    private data class Editing(
        val status: Status = Status.Loading,
        val emojis: List<String> = emptyList(),
        val pattern: EmojiPattern = EmojiPattern.STACK,
        val scale: Float = 0.5f,
        val paletteIndex: Int = 0,
        val seed: Long = EMOJI_LAYOUT_SEED,
        val groups: List<EmojiGroup> = emptyList(),
        val typeface: Typeface? = null,
    )

    private enum class Status { Loading, Failed, Ready }

    private data class Picker(
        val open: Boolean = false,
        val groupId: String = EMOJI_GROUP_RECENT,
    )

    init {
        viewModelScope.launch {
            val file = (optionalResources
                .observeState(NotoEmojiFontResource.ID)
                .first() as? OptionalResourceState.Installed)?.file
            val typeface = file?.let {
                runCatching { EmojiTypefaceLoader.load(it) }.getOrNull()
            }
            val groups = typeface?.let {
                runCatching {
                    EmojiCatalog.load(appContext, it, ioDispatcher)
                }.getOrNull()
            }
            if (typeface == null || groups.isNullOrEmpty()) {
                editing.update { it.copy(status = Status.Failed) }
                return@launch
            }
            val restored = restoreSpec()
            editing.update {
                it.copy(
                    status = Status.Ready,
                    typeface = typeface,
                    groups = groups,
                    emojis = restored?.emojis.orEmpty(),
                    pattern = restored?.pattern ?: EmojiPattern.STACK,
                    scale = restored?.scale ?: 0.5f,
                    paletteIndex = restored?.paletteIndex ?: 0,
                    seed = EMOJI_LAYOUT_SEED,
                )
            }
            if (restored == null) lucky()
        }
    }

    // ---------- SavedStateHandle 恢复 ----------

    private data class RestoredSpec(
        val emojis: List<String>,
        val pattern: EmojiPattern,
        val scale: Float,
        val paletteIndex: Int,
        val seed: Long,
    )

    private fun restoreSpec(): RestoredSpec? {
        val emojis = savedStateHandle.get<ArrayList<String>>(KEY_EMOJIS)
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return RestoredSpec(
            emojis = emojis,
            pattern = savedStateHandle.get<String>(KEY_PATTERN)
                ?.let { runCatching { EmojiPattern.valueOf(it) }.getOrNull() }
                ?: EmojiPattern.STACK,
            scale = quantizedEmojiScale(savedStateHandle.get<Float>(KEY_SCALE) ?: 0.5f),
            paletteIndex = (savedStateHandle.get<Int>(KEY_PALETTE) ?: 0)
                .coerceIn(0, palettes.lastIndex),
            seed = EMOJI_LAYOUT_SEED,
        )
    }

    private fun persistSpec() {
        val e = editing.value
        savedStateHandle[KEY_EMOJIS] = ArrayList(e.emojis)
        savedStateHandle[KEY_PATTERN] = e.pattern.name
        savedStateHandle[KEY_SCALE] = e.scale
        savedStateHandle[KEY_PALETTE] = e.paletteIndex
        savedStateHandle[KEY_SEED] = e.seed
    }

    // ---------- 编辑动作 ----------

    fun selectPattern(pattern: EmojiPattern) {
        editing.update { it.copy(pattern = pattern) }
        persistSpec()
    }

    fun setScale(scale: Float) {
        editing.update { it.copy(scale = quantizedEmojiScale(scale)) }
        persistSpec()
    }

    fun selectPalette(index: Int) {
        editing.update { it.copy(paletteIndex = index.coerceIn(0, palettes.lastIndex)) }
        persistSpec()
    }

    /** 「随机一下」：随机组（排除 flags）里抓 3–6 个表情，图案/配色/密度全随机。 */
    fun lucky() {
        val e = editing.value
        if (e.status != Status.Ready) return
        val group = e.groups.filter { it.id != "flags" }.randomOrNull(Random.Default)
            ?: return
        val count = Random.Default.nextInt(3, 7).coerceAtMost(group.emojis.size)
        editing.update {
            it.copy(
                emojis = group.emojis.shuffled(Random.Default).take(count),
                pattern = EmojiPattern.entries.random(Random.Default),
                scale = quantizedEmojiScale(0.3f + Random.Default.nextFloat() * 0.5f),
                paletteIndex = Random.Default.nextInt(palettes.size),
                seed = EMOJI_LAYOUT_SEED,
            )
        }
        persistSpec()
    }

    // ---------- 选择器 ----------

    fun openPicker() = picker.update { it.copy(open = true) }

    fun closePicker() = picker.update { it.copy(open = false) }

    fun selectPickerGroup(groupId: String) =
        picker.update { it.copy(groupId = groupId) }

    fun addEmoji(emoji: String) {
        val e = editing.value
        if (e.emojis.size >= EMOJI_WALLPAPER_MAX_EMOJIS) {
            viewModelScope.launch { mutableEvents.emit(EmojiWallpaperEvent.PickerFull) }
            return
        }
        editing.update { it.copy(emojis = it.emojis + emoji) }
        persistSpec()
        viewModelScope.launch { recentsRepository.record(emoji) }
    }

    fun removeEmoji(index: Int) {
        editing.update {
            it.copy(emojis = it.emojis.filterIndexed { i, _ -> i != index })
        }
        persistSpec()
    }

    fun clearEmojis() {
        editing.update { it.copy(emojis = emptyList()) }
        persistSpec()
    }

    /** 选择器里的 ✓：至少留一个表情才能回编辑页。 */
    fun confirmPicker() {
        if (editing.value.emojis.isNotEmpty()) closePicker()
    }

    // ---------- 导出 ----------

    /** 把当前组合按窗口像素渲成 PNG 写进缓存目录；结果走 [events]。 */
    fun export(widthPx: Int, heightPx: Int) {
        val state = uiState.value as? EmojiWallpaperUiState.Ready ?: return
        if (state.exporting) return
        exporting.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val file = runCatching {
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    val layout = emojiWallpaperLayout(
                        pattern = state.pattern,
                        emojiCount = state.emojis.size,
                        scale = state.scale,
                        seed = state.seed,
                        width = widthPx.toFloat(),
                        height = heightPx.toFloat(),
                    )
                    drawEmojiWallpaper(
                        canvas = canvas,
                        width = widthPx,
                        height = heightPx,
                        spec = state.spec,
                        layout = layout,
                        typeface = state.typeface,
                    )
                    val dir = File(appContext.cacheDir, EXPORT_DIR).apply { mkdirs() }
                    val out = File(dir, "render-${System.currentTimeMillis()}.png")
                    try {
                        out.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } catch (exception: Exception) {
                        out.delete()
                        throw exception
                    }
                    out
                } finally {
                    bitmap.recycle()
                }
            }.getOrNull()
            exporting.value = false
            mutableEvents.emit(
                file?.let(EmojiWallpaperEvent::Exported) ?: EmojiWallpaperEvent.ExportFailed,
            )
        }
    }

    private companion object {
        const val KEY_EMOJIS = "spec_emojis"
        const val KEY_PATTERN = "spec_pattern"
        const val KEY_SCALE = "spec_scale"
        const val KEY_PALETTE = "spec_palette"
        const val KEY_SEED = "spec_seed"
        const val EXPORT_DIR = "emoji-wallpaper"
    }
}

class EmojiWallpaperViewModelFactory(
    private val appContext: Context,
    private val optionalResources: OptionalResourceRepository,
    private val recentsRepository: EmojiPickerRecentsRepository,
    private val systemSeedArgb: Int?,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(EmojiWallpaperViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return EmojiWallpaperViewModel(
            savedStateHandle = extras.createSavedStateHandle(),
            appContext = appContext.applicationContext,
            optionalResources = optionalResources,
            recentsRepository = recentsRepository,
            systemSeedArgb = systemSeedArgb,
        ) as T
    }
}
