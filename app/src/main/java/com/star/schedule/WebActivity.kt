package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.star.schedule.db.DatabaseProvider
import com.star.schedule.ui.theme.StarScheduleTheme
import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.rememberCoroutineScope
import com.star.schedule.R
import com.star.schedule.ui.components.OptimizedBottomSheet
import kotlinx.coroutines.launch

class WebActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(this)
        enableEdgeToEdge()
        setContent {
            StarScheduleTheme {
                WebCaptureScreen(this, "http://shldxyjw.yinghuaonline.com/shldzyjsxy/") { html ->
                    println("网页内容: $html")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebCaptureScreen(context: WebActivity, url: String, onHtmlReceived: (String) -> Unit) {
    var webViewTitle by remember { mutableStateOf(context.getString(R.string.web_loading_title)) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(webViewTitle) },
                navigationIcon = {
                    IconButton(onClick = { context.finish() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDownloadSheet = true }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.action_download)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))

            // WebView 占剩余空间
            WebViewContent(
                context = context,
                url = url,
                onTitleReceived = { webViewTitle = it },
                onHtmlReceived = onHtmlReceived,
                modifier = Modifier.weight(1f) // 关键，避免布局无限伸缩
            )
        }
    }

    if (showDownloadSheet) {
        OptimizedBottomSheet(
            onDismiss = { showDownloadSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.download_options_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = stringResource(R.string.download_options_description),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showDownloadSheet = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.download_button_sample))
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContent(
    context: WebActivity,
    url: String,
    onTitleReceived: (String) -> Unit,
    onHtmlReceived: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = false // 禁用缩放控件
                settings.displayZoomControls = false
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = false

                isFocusable = true
                isFocusableInTouchMode = true

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url ?: return false
                        return if (url.startsWith("http://") || url.startsWith("https://")) {
                            // 正常加载
                            false
                        } else {
                            // 忽略自定义 scheme 或提示用户
                            println("忽略非 http/https URL: $url")
                            true
                        }
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onTitleReceived(view?.title ?: context.getString(R.string.unknown_title))
                        evaluateJavascript(
                            "(function() { return document.documentElement.outerHTML; })();"
                        ) { html ->
                            onHtmlReceived(html)
                        }
                    }
                }

                webChromeClient = WebChromeClient()

//                addJavascriptInterface(object {
//                    @JavascriptInterface
//                    fun processHtml(html: String) {
//                        onHtmlReceived(html)
//                    }
//                }, "AndroidBridge")

                loadUrl(url)
            }
        },
        update = { /* 不用重复设置 WebViewClient，已在 factory 内 */ },
        modifier = modifier
            .fillMaxWidth()
    )
}
