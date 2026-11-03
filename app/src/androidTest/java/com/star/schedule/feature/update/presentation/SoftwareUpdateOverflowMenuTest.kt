package com.star.schedule.feature.update.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.AppMenu
import com.star.schedule.core.designsystem.component.MenuEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SoftwareUpdateOverflowMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun openMenu() {
        composeRule
            .onNodeWithContentDescription(string(R.string.update_more_options))
            .performClick()
    }

    private fun selectedRow() = composeRule.onNode(isSelectable())

    private fun selectedLabel() =
        composeRule.onNodeWithText(string(R.string.update_include_snapshots))

    @Test
    fun selectedRowTogglesWithoutClosingMenu() {
        val includeSnapshots = mutableStateOf(false)
        var callbackCount = 0
        composeRule.setContent {
            MaterialTheme {
                SoftwareUpdateOverflowMenu(
                    includeSnapshots = includeSnapshots.value,
                    onIncludeSnapshotsChange = {
                        includeSnapshots.value = it
                        callbackCount++
                    },
                )
            }
        }

        openMenu()
        selectedRow().assertIsNotSelected().performClick()
        selectedRow().assertIsSelected().performClick()
        selectedRow().assertIsNotSelected()
        assertEquals(2, callbackCount)
        selectedLabel().assertIsDisplayed()
    }

    @Test
    fun selectedStateSurvivesDismissAndReopen() {
        val includeSnapshots = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                SoftwareUpdateOverflowMenu(
                    includeSnapshots = includeSnapshots.value,
                    onIncludeSnapshotsChange = { includeSnapshots.value = it },
                )
            }
        }

        openMenu()
        selectedRow().performClick()
        selectedRow().assertIsSelected()

        Espresso.pressBack()
        selectedLabel().assertDoesNotExist()

        openMenu()
        selectedRow().assertIsSelected()
    }

    @Test
    fun selectedStateRestoredAfterRecreation() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                SoftwareUpdateRoute(onBack = {})
            }
        }

        openMenu()
        selectedRow().assertIsNotSelected().performClick()
        selectedRow().assertIsSelected()

        Espresso.pressBack()
        restorationTester.emulateSavedInstanceStateRestore()

        openMenu()
        selectedRow().assertIsSelected()
    }

    @Test
    fun selectionKeepsRowAndLabelBounds() {
        val includeSnapshots = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                SoftwareUpdateOverflowMenu(
                    includeSnapshots = includeSnapshots.value,
                    onIncludeSnapshotsChange = { includeSnapshots.value = it },
                )
            }
        }

        openMenu()
        val label = string(R.string.update_include_snapshots)
        fun rowBounds() = selectedRow().fetchSemanticsNode().boundsInRoot
        fun labelBounds() = composeRule
            .onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        val rowUnselected = rowBounds()
        val labelUnselected = labelBounds()

        selectedRow().performClick()
        selectedRow().assertIsSelected()
        val rowSelected = rowBounds()
        val labelSelected = labelBounds()

        selectedRow().performClick()
        selectedRow().assertIsNotSelected()
        val rowReverted = rowBounds()
        val labelReverted = labelBounds()

        assertRectEqual("selected row", rowUnselected, rowSelected)
        assertRectEqual("selected label", labelUnselected, labelSelected)
        assertRectEqual("reverted row", rowUnselected, rowReverted)
        assertRectEqual("reverted label", labelUnselected, labelReverted)
    }

    @Test
    fun selectedItemLeadingSlotAlignsWithActionItem() {
        val selected = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                AppMenu(
                    expanded = true,
                    onDismissRequest = {},
                    groups = listOf(
                        listOf(
                            MenuEntry(
                                label = "Action",
                                leadingIcon = Icons.Rounded.Info,
                                onClick = {},
                            ),
                            MenuEntry(
                                label = "Choice",
                                leadingIcon = Icons.Rounded.Info,
                                selected = selected.value,
                                onClick = { selected.value = !selected.value },
                            ),
                        ),
                    ),
                )
            }
        }

        // 普通项的行同样带 Selected 语义，isSelectable 会匹配两行，按文案收窄到选择项。
        fun choiceRow() = composeRule.onNodeWithText("Choice")
        fun labelLeft(label: String) = composeRule
            .onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .left
            .toDouble()
        fun choiceRowWidth() = choiceRow()
            .fetchSemanticsNode()
            .boundsInRoot
            .width
            .toDouble()

        assertEquals("unselected label left", labelLeft("Action"), labelLeft("Choice"), 0.5)
        val widthBefore = choiceRowWidth()

        choiceRow().performClick()
        choiceRow().assertIsSelected()

        assertEquals("selected label left", labelLeft("Action"), labelLeft("Choice"), 0.5)
        assertEquals("selectable row width", widthBefore, choiceRowWidth(), 0.5)
    }

    private fun assertRectEqual(what: String, expected: Rect, actual: Rect) {
        assertEquals("$what left", expected.left.toDouble(), actual.left.toDouble(), 0.5)
        assertEquals("$what right", expected.right.toDouble(), actual.right.toDouble(), 0.5)
        assertEquals("$what width", expected.width.toDouble(), actual.width.toDouble(), 0.5)
        assertEquals("$what height", expected.height.toDouble(), actual.height.toDouble(), 0.5)
    }
}
