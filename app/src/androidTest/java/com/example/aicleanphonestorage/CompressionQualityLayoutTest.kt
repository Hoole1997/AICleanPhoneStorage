package com.example.aicleanphonestorage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.databinding.DialogCompressionQualityBinding
import com.example.aicleanphonestorage.feature.filecleaner.ui.CompressionQualityDialog
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompressionQualityLayoutTest {
    @Test
    fun normalAndLargeFontsMeasureWithoutClipping() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (scale in listOf(1f, 2f)) {
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val config =
                    Configuration(context.resources.configuration).apply { fontScale = scale }
                val themed =
                    ContextThemeWrapper(
                        context.createConfigurationContext(config),
                        R.style.Theme_AICleanPhoneStorage,
                    )
                val fragment = CompressionQualityDialog.create(1, 75)
                val root = fragment.onCreateView(LayoutInflater.from(themed), null, null)
                val binding = DialogCompressionQualityBinding.bind(root)
                val density = themed.resources.displayMetrics.density
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(
                        (288 * density).toInt(),
                        View.MeasureSpec.EXACTLY,
                    ),
                    View.MeasureSpec.makeMeasureSpec(
                        (560 * density).toInt(),
                        View.MeasureSpec.AT_MOST,
                    ),
                )
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                for (text in
                    listOf(
                        binding.qualityTitle,
                        binding.qualitySmall,
                        binding.qualityBalanced,
                        binding.qualityHigh,
                    )) {
                    assertTrue(
                        text.height >=
                            text.layout.height +
                                text.compoundPaddingTop +
                                text.compoundPaddingBottom
                    )
                }
                assertTrue(binding.qualityBalanced.isChecked)
                assertFalse(binding.qualitySmall.isChecked)
                assertTrue(root.height <= 560 * density)
                if (scale == 1f)
                    assertTrue("Normal dialog should remain compact", root.height <= 340 * density)
                bitmap =
                    Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                        root.draw(Canvas(it))
                    }
            }
            val directory =
                File(context.getExternalFilesDir(null), "quality-tests").apply { mkdirs() }
            bitmap!!.let { image ->
                File(directory, "quality_$scale.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
        }
    }
}
