package com.thatmarcel.apps.railochrone.helpers

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

class ProgressResponseBody(val responseBody: ResponseBody?, val progressListener: ProgressListener): ResponseBody() {
    private lateinit var bufferedSource: BufferedSource

    override fun contentLength(): Long {
        return responseBody?.contentLength() ?: 0L
    }

    override fun contentType(): MediaType? {
        return responseBody?.contentType()
    }

    override fun source(): BufferedSource {
        if (!this::bufferedSource.isInitialized) {
            bufferedSource = object : ForwardingSource(responseBody!!.source()) {
                var totalReadBytesCount = 0L

                override fun read(sink: Buffer, byteCount: Long): Long {
                    val readBytesCount = super.read(sink, byteCount)

                    if (readBytesCount != -1L) {
                        totalReadBytesCount += readBytesCount
                    }

                    progressListener.update(
                        totalReadBytesCount.toDouble() / (responseBody?.contentLength()?.toDouble() ?: 1.0),
                        readBytesCount == -1L
                    )

                    return readBytesCount
                }
            }.buffer()
        }

        return bufferedSource
    }

    interface ProgressListener {
        fun update(progressFraction: Double, hasFinished: Boolean)
    }
}