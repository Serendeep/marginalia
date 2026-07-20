package com.serendeep.marginalia.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.data.CourseEntity
import com.serendeep.marginalia.data.DocumentEntity
import com.serendeep.marginalia.data.LectureEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MarginaliaRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val importer = PdfImporter(context, repository)

    val courses: StateFlow<List<CourseEntity>> = repository.observeCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun lecturesOf(courseId: String): Flow<List<LectureEntity>> = repository.observeLectures(courseId)

    fun documentsOf(lectureId: String): Flow<List<DocumentEntity>> = repository.observeDocuments(lectureId)

    fun createCourse(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createCourse(name.trim()) }
    }

    fun createLecture(courseId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.createLecture(courseId, title.trim()) }
    }

    fun importPdf(lectureId: String, uri: Uri) {
        viewModelScope.launch {
            when (val result = importer.import(lectureId, uri)) {
                is PdfImporter.Result.Success -> _error.value = null
                is PdfImporter.Result.Failure -> _error.value = result.message
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
