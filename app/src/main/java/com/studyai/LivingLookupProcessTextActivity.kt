package com.studyai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.studyai.data.api.ApiClient
import com.studyai.ui.dictionary.LivingLookupBottomSheet
import com.studyai.ui.theme.StudyAiTheme
import com.studyai.viewmodel.StudyAiViewModel

/**
 * Activity xử lý menu ngữ cảnh hệ thống Android (ACTION_PROCESS_TEXT):
 * Cho phép sinh viên bôi đen chữ ở bất kỳ trình đọc PDF / Chrome / Google Docs nào và mở thẳng "Từ điển sống".
 */
class LivingLookupProcessTextActivity : ComponentActivity() {

    private val viewModel: StudyAiViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StudyAiViewModel(ApiClient.apiService, applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ApiClient.init(applicationContext)

        val selectedText = (
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: ""
            ).trim()

        if (selectedText.isEmpty()) {
            finish()
            return
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            val lookupResult by viewModel.activeLivingLookup.collectAsState()
            val isLookingUp by viewModel.isLookingUpLivingTerm.collectAsState()
            val savedEntries by viewModel.savedLivingEntries.collectAsState()

            LaunchedEffect(selectedText) {
                viewModel.loadSavedLivingEntries(1L)
                viewModel.lookupLivingTerm(
                    selectedText = selectedText,
                    surroundingSentence = selectedText,
                    courseId = null,
                    lectureId = null,
                    slideNumber = null,
                    originLabel = "Tra cứu ngoài ứng dụng",
                )
            }

            StudyAiTheme(darkTheme = isDark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    lookupResult?.let { result ->
                        val isSaved = savedEntries.any { it.term.equals(result.term, ignoreCase = true) }
                        LivingLookupBottomSheet(
                            result = result,
                            isAlreadySaved = isSaved,
                            isLoading = isLookingUp,
                            jumpButtonLabel = "Đóng",
                            onDismiss = {
                                viewModel.dismissLivingLookup()
                                finish()
                            },
                            onJumpToOrigin = { _, _ ->
                                viewModel.dismissLivingLookup()
                                finish()
                            },
                            onSaveToVault = { item ->
                                viewModel.saveLivingLookupToVault(item)
                                Toast.makeText(
                                    this@LivingLookupProcessTextActivity,
                                    "Đã lưu \"${item.term}\" vào Kho Từ điển sống",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        )
                    }
                }
            }
        }
    }
}
