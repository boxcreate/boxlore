package cx.aswin.boxlore.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cx.aswin.boxlore.tv.ui.TvApp
import cx.aswin.boxlore.tv.ui.theme.BoxloreTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TvApplication).container
        setContent {
            BoxloreTvTheme {
                TvApp(container)
            }
        }
    }
}
