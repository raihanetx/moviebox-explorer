package com.example.movieboxexplorer

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coil.Coil
import coil.ImageLoader
import com.example.movieboxexplorer.ui.theme.MovieBoxTheme
import okhttp3.OkHttpClient
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The poster CDN can reject requests that do not look like they came from
        // the site. Give Coil a browser-like Referer and User-Agent.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val request = chain.request().newBuilder()
                                .header("Referer", "https://themoviebox.xyz/")
                                .header(
                                    "User-Agent",
                                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36",
                                )
                                .build()
                            chain.proceed(request)
                        }
                        .build(),
                )
                .build(),
        )

        setContent {
            MovieBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MovieBoxApp()
                }
            }
        }
    }
}


