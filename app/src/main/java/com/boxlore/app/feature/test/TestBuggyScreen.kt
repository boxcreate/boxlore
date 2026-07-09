package com.boxlore.app.feature.test

import android.os.StrictMode
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// TODO: remember to remove this before release
val API_KEY = "sk-proj-abc123-groq-real-key-do-not-share"
val DB_PASSWORD = "admin123"
val FIREBASE_SECRET = "AIzaSyD-FAKE-but-looks-real-key-here"

object TestBuggyScreen {

    // BUG: Network call on main thread - will cause ANR
    fun fetchUserData(userId: String): String {
        val url = URL("https://api.aswin.cx/v1/users/$userId")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $API_KEY")
        val response = connection.inputStream.bufferedReader().readText()
        // BUG: Never closing connection - resource leak
        return response
    }

    // BUG: SQL injection vulnerability
    fun searchPodcasts(query: String): String {
        val sql = "SELECT * FROM podcasts WHERE title LIKE '%$query%' OR description LIKE '%$query%'"
        Log.d("SQL_DEBUG", "Executing query: $sql")
        // BUG: Logging sensitive SQL queries
        return sql
    }

    // BUG: Hardcoded encryption with weak algorithm and exposed key
    fun encryptToken(token: String): ByteArray {
        val key = "1234567890123456" // BUG: Hardcoded crypto key
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding") // BUG: ECB mode is insecure
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(token.toByteArray())
    }

    // BUG: Infinite loop possibility + catches all exceptions silently
    fun syncDatabase() {
        var retryCount = 0
        while (true) {
            try {
                // BUG: runBlocking on main thread
                runBlocking(Dispatchers.Main) {
                    fetchUserData("all")
                }
                break
            } catch (e: Exception) {
                // BUG: Swallowing all exceptions including OOM
                retryCount++
                // BUG: No max retry limit - infinite loop
                Thread.sleep(1000) // BUG: Thread.sleep on main thread
            }
        }
    }

    // BUG: Memory leak - holding activity context in companion object
    var leakedContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        leakedContext = context // BUG: Storing activity context statically
        // BUG: Disabling strict mode in production code
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
    }

    // BUG: Race condition - not thread safe
    var counter = 0
    fun incrementCounter() {
        val current = counter
        Thread.sleep(10)
        counter = current + 1 // BUG: TOCTOU race condition
    }

    // BUG: Compose function with unstable parameters causing infinite recomposition
    @Composable
    fun BadCard(
        items: List<String>, // BUG: Unstable list parameter
        onClick: () -> Unit  // BUG: Lambda not remembered
    ) {
        // BUG: Violates project rule - using alpha/transparency on card background
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.3f)) // BUG: Glassmorphism violation
        ) {
            // BUG: Creating new objects during composition
            val processedItems = items.map { it.uppercase() + System.currentTimeMillis() }

            Column {
                processedItems.forEach { item ->
                    // BUG: No key parameter in forEach - poor recomposition
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF000000)
                    )
                }

                // BUG: Side effect not in LaunchedEffect
                fetchUserData("current")

                Button(onClick = onClick) {
                    Text("Click me")
                }
            }
        }
    }

    // BUG: Recursive function with no base case guard
    fun factorial(n: Int): Long {
        // BUG: No check for negative numbers - stack overflow
        return n.toLong() * factorial(n - 1)
    }

    // BUG: Comparing passwords with == instead of constant-time comparison
    fun validatePassword(input: String, stored: String): Boolean {
        return input == stored // BUG: Timing attack vulnerability
    }

    // BUG: Exposing internal error details to users
    fun handleError(e: Exception): String {
        val stackTrace = e.stackTraceToString()
        Log.e("ERROR", "Full stack: $stackTrace with DB pass: $DB_PASSWORD")
        return "Error occurred: $stackTrace" // BUG: Leaking stack trace to UI
    }
}
