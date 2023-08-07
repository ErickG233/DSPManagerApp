package com.cyanogenmod.dspmanager.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class BiQuad {
    private lateinit var b0: Complex
    private lateinit var b1: Complex
    private lateinit var b2: Complex
    private lateinit var a0: Complex
    private lateinit var a1: Complex
    private lateinit var a2: Complex

    fun setHighShelf(centerFrequency: Double, samplingFrequency: Double,
                               dbGain: Double, slope: Double) {
        val w0 = 2 * PI * centerFrequency / samplingFrequency
        val a = 10.0.pow(dbGain / 40)
        val alpha = sin(w0) / 2 * sqrt((a + 1/a) * (1 / slope - 1) + 2)

        b0 = Complex(a * ((a + 1) + (a - 1) * cos(w0) + 2 * sqrt(a) * alpha), 0.0)
        b1 = Complex(-2 * a * ((a - 1) + (a + 1) * cos(w0)), 0.0)
        b2 = Complex(a * ((a + 1) + (a - 1) * cos(w0) - 2 * sqrt(a) * alpha), 0.0)
        a0 = Complex((a + 1) - (a - 1) * cos(w0) + 2 * sqrt(a) * alpha, 0.0)
        a1 = Complex(2 * ((a - 1) - (a + 1) * cos(w0)), 0.0)
        a2 = Complex((a + 1) - (a - 1) * cos(w0) - 2 * sqrt(a) * alpha, 0.0)
    }

    fun evaluateTransfer(z: Complex): Complex {
        val zSquared = z.mul(z)
        val nom = b0.add(b1.div(z)).add(b2.div(zSquared))
        val den = a0.add(a1.div(z)).add(a2.div(zSquared))
        return nom.div(den)
    }

}