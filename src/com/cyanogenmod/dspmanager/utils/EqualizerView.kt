package com.cyanogenmod.dspmanager.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import com.cyanogenmod.dspmanager.R
import java.util.Locale
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.sin

class EqualizerView constructor(context: Context, attributeSet: AttributeSet? = null) :
    SurfaceView(context, attributeSet) {

    private var mWidth: Int = 0                           /* 视图宽度 */
    private var mHeight: Int = 0                          /* 视图高度 */

    private lateinit var freqResponse: Path               /* 波段波浪线 */
    private lateinit var freqResponseBg: Path             /* 波段背景填充色 */

    /* 是否为对话框 */
    private var isDialog = false

    /* 频率条波段(这段是给可视化图形用的) */
    private val mVisibleLevels = FloatArray(6)

    /* 频率条波段(这段是给音效用的) */
    private val mEffectLevels = FloatArray(6)

    /* 频率条波段更新位置 */
    private var bandIndex:Int = -1
    /* 频率条波段音效更新值 */
    private var mBandLevel:Float = -1f

    /* 设置频谱的变更监听器 */
    interface BandChangedListener {
        fun onBandValueChanged(bandIndex: (Int), bandLevel: (Float))
    }

    private var mBandChangedListener: BandChangedListener ?= null

    fun setBandChangedListener(l: (Int, Float) -> Unit) {
        mBandChangedListener = object : BandChangedListener {
            override fun onBandValueChanged(bandIndex: Int, bandLevel: Float) { l(bandIndex, bandLevel) }
        }
    }

    companion object {
        /* 设置最低频率 10HZ, 最高频率 21000HZ */
        private const val MIN_FREQ = 10.0
        private const val MAX_FREQ = 21000.0
        /* 设置增益 正负12 分贝 */
        private const val MIN_DB = -12
        private const val MAX_DB = 12
    }

    /* 设置频率条样式 */
    private fun initControlBar(): LinearGradient {
        return LinearGradient(0f, 0f, 0f, mHeight.toFloat(),
            intArrayOf(
                context.getColor(R.color.bar_shader),
                context.getColor(R.color.bar_shader_alpha)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    /* 设置波段背景 */
    private fun initFrequencyResponseBg(): LinearGradient {
        return LinearGradient(0f, 0f,0f, mHeight.toFloat(),
            intArrayOf(
                context.getColor(R.color.eq_max_red),
                context.getColor(R.color.eq_high_yellow),
                context.getColor(R.color.eq_flat_bright),
                context.getColor(R.color.eq_low_blue),
                context.getColor(R.color.eq_bottom_dark)
            ),
            floatArrayOf(0f, .2f, .45f, .6f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    /* 画笔工具 */
    private val mWhite : Paint                                   /*  */
    private val mGridLines : Paint                               /* 网格线 */
    private val mHorizontalStepText : Paint                      /* 水平等级值 */
    private val mControlBarText : Paint                          /* 频率条对应值 */
    private val mControlBar : Paint                              /* 频率条 */
    private val mControlBarKnob : Paint                          /* 频率条顶部小点 */
    private val mFrequencyResponseBg : Paint                     /* 波段背景 */
    private val mFrequencyResponseHighlight : Paint              /* 波段高亮背景1 */
    private val mFrequencyResponseHighlight2 : Paint             /* 波段高亮背景2 */

    /* 初始化 */
    init {
        setWillNotDraw(false)
        mWhite = Paint()
        mWhite.color = context.getColor(R.color.white)
        mWhite.style = Paint.Style.STROKE    /* 加粗 */
        mWhite.textSize = 13f
        mWhite.isAntiAlias = true            /* 抗锯齿 */

        mGridLines = Paint()
        mGridLines.color = context.getColor(R.color.grid_lines)
        mGridLines.style = Paint.Style.STROKE

        mHorizontalStepText = Paint()
        mHorizontalStepText.color = context.getColor(R.color.white)
        mHorizontalStepText.textSize = DimensionConvert.spToPixels(7f, context).toFloat()

        mControlBarText = Paint()
        mControlBarText.color = context.getColor(R.color.white)
        mControlBarText.textAlign = Paint.Align.CENTER
        mControlBarText.textSize = DimensionConvert.spToPixels(9f, context).toFloat()
        mControlBarText.setShadowLayer(2f, 0f, 0f, context.getColor(R.color.bar_top))

        mControlBar = Paint()
        mControlBar.style = Paint.Style.STROKE
        mControlBar.color = context.getColor(R.color.bar_top)
        mControlBar.isAntiAlias = true
        mControlBar.strokeCap = Paint.Cap.ROUND
        mControlBar.setShadowLayer(2f, 0f, 0f, context.getColor(R.color.black))

        mControlBarKnob = Paint()
        mControlBarKnob.style = Paint.Style.FILL
        mControlBarKnob.color = context.getColor(R.color.white)
        mControlBarKnob.isAntiAlias = true

        mFrequencyResponseBg = Paint()
        mFrequencyResponseBg.style = Paint.Style.FILL
        mFrequencyResponseBg.isAntiAlias = true

        mFrequencyResponseHighlight = Paint()
        mFrequencyResponseHighlight.style = Paint.Style.STROKE
        mFrequencyResponseHighlight.strokeWidth = 6f
        mFrequencyResponseHighlight.color = context.getColor(R.color.freq_hl_dark)
        mFrequencyResponseHighlight.isAntiAlias = true

        mFrequencyResponseHighlight2 = Paint()
        mFrequencyResponseHighlight2.style = Paint.Style.STROKE
        mFrequencyResponseHighlight2.strokeWidth = 3f
        mFrequencyResponseHighlight2.color = context.getColor(R.color.freq_hl_fade)
        mFrequencyResponseHighlight2.isAntiAlias = true
    }

    /* 设置硬件绘图 */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        buildLayer()
    }

    /* 布局设置 */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        mWidth = right - left
        mHeight = bottom - top

        /* 频率条宽度 */
        val barWidth = DimensionConvert.dimensionToPixels(7f, context).toFloat()
        mControlBar.strokeWidth = barWidth
        mControlBarKnob.setShadowLayer(barWidth * .5f, 0f, 0f, context.getColor(R.color.off_white))
        /* 频率波段背景 */
        mFrequencyResponseBg.shader = initFrequencyResponseBg()
        /* 频率条样式 */
        mControlBar.shader = initControlBar()
    }

    private fun projectX(freq: Double): Float {
        val pos = log(freq, E)
        val minPos = log(MIN_FREQ, E)
        val maxPos = log(MAX_FREQ, E)
        return ((pos - minPos) / (maxPos - minPos)).toFloat()
    }

    private fun reverseProjectX(pos: Float): Double {
        val minPos = log(MIN_FREQ, E)
        val maxPos = log(MAX_FREQ, E)
        return exp(pos.toDouble() * (maxPos - minPos) + minPos)
    }

    private fun projectY(db: Double): Float {
        val pos = (db - MIN_DB) / (MAX_DB - MIN_DB)
        return (1 - pos).toFloat()
    }

    private fun lin2db(rho: Double): Double {
        return if (rho != 0.0) log(rho, E) / log(10.0, E) * 20 else -99.9
    }

    private fun initOnDraw() {
        val biQuads = arrayOf(BiQuad(), BiQuad(), BiQuad(), BiQuad(), BiQuad())
        /* The filtering is realized with 2nd order high shelf filters, and each band
         * is realized as a transition relative to the previous band. The center point for
         * each filter is actually between the bands.
         *
         * 1st band has no previous band, so it's just a fixed gain.
         */
        val gain = 10.0.pow(mVisibleLevels[0] / 20.0)
        for (index in biQuads.indices) {
            val freq = 15.625 * 4.0.pow(index)
            val samplingRate = 48000.0
            biQuads[index].setHighShelf(freq * 2, samplingRate,(mVisibleLevels[index + 1] - mVisibleLevels[index]).toDouble(), 1.0)
        }

        freqResponse = Path()
        for (i in 0..70) {
            val freq = reverseProjectX(i / 70f)
            val samplingRate = 48000.0
            val omega = freq / samplingRate * PI * 2
            val z = Complex(cos(omega), sin(omega))
            /* Evaluate the response at frequency z */

            /* Complex z1 = z.mul(gain); */
            val z2 = biQuads[0].evaluateTransfer(z)
            val z3 = biQuads[1].evaluateTransfer(z)
            val z4 = biQuads[2].evaluateTransfer(z)
            val z5 = biQuads[3].evaluateTransfer(z)
            val z6 = biQuads[4].evaluateTransfer(z)

            /* Magnitude response, dB */
            val db = lin2db(gain * z2.rho() * z3.rho() * z4.rho() * z5.rho() * z6.rho())
            val x = projectX(freq) * mWidth
            val y = projectY(db) * mHeight

            /* Set starting point at first point */
            if (i == 0)
                freqResponse.moveTo(x, y)
            else
                freqResponse.lineTo(x, y)
        }

        freqResponseBg = Path()
        freqResponseBg.addPath(freqResponse)
        freqResponseBg.offset(0f, -4f)
        freqResponseBg.lineTo(mWidth.toFloat(), mHeight.toFloat())
        freqResponseBg.lineTo(0f, mHeight.toFloat())
        freqResponseBg.close()
    }

    /* 绘图部分 */
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        /* clear canvas */
        canvas?.drawRGB(0, 0, 0)
        initOnDraw()

        canvas?.drawPath(freqResponseBg, mFrequencyResponseBg)
        canvas?.drawPath(freqResponse, mFrequencyResponseHighlight)
        canvas?.drawPath(freqResponse, mFrequencyResponseHighlight2)

        /* Set the width of the bars according to canvas size */
        canvas?.drawRect(0f, 0f, (mWidth - 1).toFloat(), (mHeight - 1).toFloat(), mWhite)

        /* draw vertical lines */
        /* 绘画竖直线 */
        var vtLines = MIN_FREQ
        while (vtLines < MAX_FREQ) {
            val x = projectX(vtLines) * mWidth
            canvas?.drawLine(x, 0f, x, (mHeight - 1).toFloat(), mGridLines)
            vtLines += if (vtLines < 100.0) {
                10.0
            } else if (vtLines < 1000.0) {
                100.0
            } else if (vtLines < 10000.0) {
                1000.0
            } else {
                10000.0
            }
        }

        /* draw horizontal lines */
        /* 绘画水平线(每隔三段增益画一条)
         * 绘画阶梯值 */
        for (stepDB in MIN_DB + 3 .. MAX_DB -3 step 3) {
            val y = projectY(stepDB.toDouble()) * mHeight.toFloat()
            canvas?.drawLine(0f, y, (mWidth - 1).toFloat(), y, mGridLines)
            canvas?.drawText(String.format(Locale.ROOT, "%+d", stepDB), 1f, (y - 1f), mHorizontalStepText)
        }

        for (i in mVisibleLevels.indices) {
            val freq = 15.625 * 4.0.pow(i)
            val x = projectX(freq) * mWidth.toFloat()
            val y = projectY(mVisibleLevels[i].toDouble()) * mHeight.toFloat()
            canvas?.drawLine(x, mHeight.toFloat(), x, y, mControlBar)
            canvas?.drawCircle(x, y, mControlBar.strokeWidth * 0.66f, mControlBarKnob)
            /* 底部频率条增益数值 */
            canvas?.drawText(String.format("%+1.1f", mVisibleLevels[i]), x, (mHeight - 4).toFloat(), mControlBarText)
            /* 顶部频段数值 */
            canvas?.drawText(String.format(if (freq < 1000.0) "%.0f" else "%.0fk", if (freq < 1000.0) freq.toFloat() else (freq / 1000.0).toFloat()),
                x, mControlBarText.textSize, mControlBarText
            )
        }

    }

    /* 使用dispatchEvent了 */
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (isDialog) {
            val touchX = event?.x ?: 0f
            val touchY = event?.y ?: 0f
            /* 锁死按下的距离最近的频率条 */
            /* 不下发触摸事件 */
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    /* 获取水平坐标内距离触摸位置最短的频率条 */
                    bandIndex = getClosestBand(touchX)
                }
                MotionEvent.ACTION_MOVE -> {
                    /* 根据触摸点设置频率条增益值 */
                    var bandLevel = (touchY / height) * (MIN_DB - MAX_DB) - MIN_DB
                    if (bandLevel < MIN_DB)
                        bandLevel = MIN_DB.toFloat()
                    else if (bandLevel > MAX_DB)
                        bandLevel = MAX_DB.toFloat()

                    /* 只有值变了才进行音效更新, 否则过度使用会出问题 */
                    mBandLevel = String.format(Locale.ROOT,"%.1f", bandLevel).toFloat()
                    if (mEffectLevels[bandIndex] != mBandLevel) {
                        setEffectedSingleBand(bandIndex, mBandLevel)
                        mBandChangedListener?.onBandValueChanged(bandIndex, mBandLevel)
                    }
                    /* 设置可视化频率条 */
                    setSingleVisibleBand(bandIndex, bandLevel)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    /* 重新下发触摸事件 */
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            /* 消费点击事件 */
            return true
        }
        /* 继续分配点击事件 */
        return super.dispatchTouchEvent(event)
    }

    /* 设置为对话框 */
    fun forDialog() { isDialog = true }

    /* 外部方法, 设置单个可视化频率条 */
    fun setSingleVisibleBand(index: Int, value: Float) {
        mVisibleLevels[index] = value
    }

    /* 外部方法, 设置单个正在影响音效的频率条 */
    fun setEffectedSingleBand(index: Int, value: Float) {
        mEffectLevels[index] = value
    }

    /* 外部方法, 获取单个频率条 */
    fun getSingleBand(index: Int): Float {
        return mVisibleLevels[index]
    }

    /* 获取离触摸位置最近的频率条 */
    private fun getClosestBand(px: Float): Int {
        var idx = 0
        var closest = 1e9f
        for (i in mVisibleLevels.indices) {
            val freq = 15.625 * 4.0.pow(i)
            val cx = projectX(freq) * mWidth
            val distance = abs(cx - px)               /* 计算绝对距离 */
            if (distance < closest) {
                idx = i
                closest = distance
            }
        }
        return idx
    }
}