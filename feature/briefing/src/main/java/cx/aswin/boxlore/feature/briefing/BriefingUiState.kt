package cx.aswin.boxlore.feature.briefing

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter

sealed interface BriefingUiState {
    data object Loading : BriefingUiState
    
    data class Success(
        val briefing: Briefing,
        val selectedRegion: String,
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val isBuffering: Boolean = false,
        val chapters: List<Chapter> = emptyList()
    ) : BriefingUiState
    
    data class Error(
        val message: String,
        val selectedRegion: String
    ) : BriefingUiState
}
