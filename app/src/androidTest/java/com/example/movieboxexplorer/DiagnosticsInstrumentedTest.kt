package com.example.movieboxexplorer

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.movieboxexplorer.data.MovieBoxApi
import com.example.movieboxexplorer.data.MovieBoxRepository
import com.example.movieboxexplorer.data.diagnostics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the same diagnostics report on a device/emulator.
 *
 * The report is printed to logcat and written to `filesDir/diagnostics_report.txt` so
 * CI can upload it. It does not fail on region-specific mobile checks — the report is
 * the deliverable; the summary shows which layers failed.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsInstrumentedTest {

    @Test
    fun diagnosticsReport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = MovieBoxRepository(MovieBoxApi())

        val report = repository.diagnostics(
            host = "themoviebox.xyz",
            appVersion = "androidTest",
            apiBase = "https://h5-api.aoneroom.com",
            siteHost = "themoviebox.xyz",
            cacheBytes = 0,
        )

        val text = report.text()
        Log.i("DiagnosticsTest", "DIAGNOSTICS_REPORT_START\n$text\nDIAGNOSTICS_REPORT_END")
        File(context.filesDir, "diagnostics_report.txt").writeText(text)

        assertTrue("No diagnostics checks ran", report.results.isNotEmpty())
        repository.close()
    }
}