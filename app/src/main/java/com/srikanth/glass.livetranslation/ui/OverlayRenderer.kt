package com.srikanth.glass.livetranslation.ui

import android.graphics.*
import android.view.SurfaceHolder
import android.view.SurfaceView

class OverlayRenderer(private val surfaceView: SurfaceView) : SurfaceHolder.Callback {
    private var renderThread: RenderThread? = null
    private val paint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    init {
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderThread = RenderThread(holder)
        renderThread?.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderThread?.shutdown()
        renderThread = null
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    inner class RenderThread(private val holder: SurfaceHolder) : Thread() {
        private var running = true

        override fun run() {
            while (running) {
                val canvas = holder.lockCanvas()
                canvas?.let {
                    drawFrame(it)
                    holder.unlockCanvasAndPost(it)
                }
                sleep(33) // ~30 FPS
            }
        }

        private fun drawFrame(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            // Draw UI elements here
        }

        fun shutdown() {
            running = false
        }
    }
}