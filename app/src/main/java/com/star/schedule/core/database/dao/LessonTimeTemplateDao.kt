package com.star.schedule.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LessonTimeTemplateDao {
    @Query("SELECT * FROM lesson_time_template ORDER BY updatedAt DESC")
    abstract fun getLessonTimeTemplatesFlow(): Flow<List<LessonTimeTemplateEntity>>

    @Query("SELECT * FROM lesson_time_template WHERE name = :name LIMIT 1")
    abstract suspend fun getLessonTimeTemplateByNameOnce(name: String): LessonTimeTemplateEntity?

    @Query("SELECT * FROM lesson_time_template WHERE id = :templateId LIMIT 1")
    abstract suspend fun getLessonTimeTemplateByIdOnce(templateId: Long): LessonTimeTemplateEntity?

    @Insert
    abstract suspend fun insertLessonTimeTemplate(template: LessonTimeTemplateEntity): Long

    @Update
    abstract suspend fun updateLessonTimeTemplate(template: LessonTimeTemplateEntity)

    @Delete
    abstract suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity)

    @Insert
    abstract suspend fun insertLessonTimeTemplateItems(items: List<LessonTimeTemplateItemEntity>)

    @Query("DELETE FROM lesson_time_template_item WHERE templateId = :templateId")
    abstract suspend fun deleteLessonTimeTemplateItemsByTemplateId(templateId: Long)

    @Query("SELECT * FROM lesson_time_template_item WHERE templateId = :templateId ORDER BY period ASC")
    abstract suspend fun getLessonTimeTemplateItemsOnce(templateId: Long): List<LessonTimeTemplateItemEntity>

    /**
     * 全部模板的节次，给列表算「共几节、几点到几点」用。
     *
     * 模板数量很少，一次取全量在内存里分组，比给每个模板单独查一次更省事。
     */
    @Query("SELECT * FROM lesson_time_template_item")
    abstract fun getLessonTimeTemplateItemsFlow(): Flow<List<LessonTimeTemplateItemEntity>>

    @Transaction
    open suspend fun replaceLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) {
        updateLessonTimeTemplate(template)
        deleteLessonTimeTemplateItemsByTemplateId(template.id)
        if (items.isNotEmpty()) {
            insertLessonTimeTemplateItems(items)
        }
    }

    @Transaction
    open suspend fun restoreLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) {
        insertLessonTimeTemplate(template)
        if (items.isNotEmpty()) {
            insertLessonTimeTemplateItems(items)
        }
    }

    @Transaction
    open suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis(),
    ): Long {
        val existing = getLessonTimeTemplateByNameOnce(templateName)

        val templateId = if (existing == null) {
            insertLessonTimeTemplate(
                LessonTimeTemplateEntity(
                    name = templateName,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                ),
            )
        } else {
            if (!overwrite) throw IllegalStateException("TEMPLATE_EXISTS")
            updateLessonTimeTemplate(existing.copy(updatedAt = updatedAt))
            deleteLessonTimeTemplateItemsByTemplateId(existing.id)
            existing.id
        }

        if (lessonTimes.isNotEmpty()) {
            insertLessonTimeTemplateItems(
                lessonTimes.map { item ->
                    item.copy(
                        id = 0,
                        templateId = templateId,
                    )
                },
            )
        }

        return templateId
    }
}
