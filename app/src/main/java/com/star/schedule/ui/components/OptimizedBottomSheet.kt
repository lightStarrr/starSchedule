package com.star.schedule.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.launch

/**
 * 优化的BottomSheet组件，提供统一的实现方式
 * 
 * @param onDismiss 当BottomSheet被关闭时的回调
 * @param sheetState BottomSheet的状态
 * @param modifier 修饰符
 * @param shape BottomSheet的形状
 * @param content BottomSheet的内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizedBottomSheet(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = androidx.compose.material3.MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    
    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
            }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    onDismiss()
                }
            }
        },
        sheetState = sheetState,
        modifier = modifier,
        shape = shape,
        content = content
    )
}

///**
// * 用于隐藏BottomSheet的扩展函数
// *
// * @param sheetState BottomSheet的状态
// * @param onDismiss 当BottomSheet完全隐藏后的回调
// */
//@OptIn(ExperimentalMaterial3Api::class)
//fun hideBottomSheet(
//    sheetState: SheetState,
//    scope: kotlinx.coroutines.CoroutineScope,
//    onDismiss: () -> Unit
//) {
//    scope.launch {
//        sheetState.hide()
//    }.invokeOnCompletion {
//        if (!sheetState.isVisible) {
//            onDismiss()
//        }
//    }
//}