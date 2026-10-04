package cx.aswin.boxlore.lifecycle

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackgroundCheckBackupRulesTest {
    @Test fun consentIsExcludedFromLegacyBackupCloudBackupAndDeviceTransfer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((resource, expected) in listOf(R.xml.backup_rules to 1, R.xml.data_extraction_rules to 2)) {
            val xml = context.resources.getXml(resource)
            var exclusions = 0
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG || xml.name != "exclude") continue
                if (xml.getAttributeValue(null, "domain") == "file" &&
                    xml.getAttributeValue(null, "path") == "datastore/auto_download_background.preferences_pb"
                ) {
                        exclusions++
                    }
            }
            xml.close()
            assertEquals(expected, exclusions)
        }
    }
}
