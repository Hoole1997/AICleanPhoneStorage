package io.docview.push.utils

import android.app.PendingIntent
import android.graphics.Bitmap
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

class ViewBuilder(
    private val packageName: String,
    private val layoutIdAboveAndroid12: Int,
    private val layoutIdBelowAndroid12: Int
) {
    private var remoteViews: RemoteViews = RemoteViews(
        packageName, if (android.os.Build.VERSION.SDK_INT >= 31) {
            layoutIdAboveAndroid12
        } else {
            layoutIdBelowAndroid12
        }
    )

    fun setViewVisible(viewId: Int, isVisible:Boolean): ViewBuilder {
        remoteViews.setViewVisibility(viewId, if (isVisible) View.VISIBLE else View.GONE)
        return this
    }

    fun setTextViewText(viewId: Int, text: CharSequence): ViewBuilder {
        remoteViews.setTextViewText(viewId, text)
        return this
    }

    fun setTextViewTextSize(viewId: Int, size: Float): ViewBuilder {
        remoteViews.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, size)
        return this
    }

    fun setImageViewResource(viewId: Int, resId: Int): ViewBuilder {
        remoteViews.setImageViewResource(viewId, resId)
        return this
    }

    fun setImageViewBitmap(viewId: Int, bitmap: Bitmap): ViewBuilder {
        remoteViews.setImageViewBitmap(viewId, bitmap)
        return this
    }

    fun setImageViewUri(viewId: Int, uri: Uri): ViewBuilder {
        remoteViews.setImageViewUri(viewId, uri)
        return this
    }

    fun setOnClickPendingIntent(viewId: Int, pendingIntent: PendingIntent): ViewBuilder {
        remoteViews.setOnClickPendingIntent(viewId, pendingIntent)
        return this
    }

    fun setProgressBar(
        viewId: Int, max: Int, progress: Int, indeterminate: Boolean
    ): ViewBuilder {
        remoteViews.setProgressBar(viewId, max, progress, indeterminate)
        return this
    }

    fun setViewVisibility(viewId: Int, visibility: Int): ViewBuilder {
        remoteViews.setViewVisibility(viewId, visibility)
        return this
    }

    fun build(): RemoteViews {
        return remoteViews
    }
}
