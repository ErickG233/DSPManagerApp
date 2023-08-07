package com.cyanogenmod.dspmanager.utils

import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class Complex constructor(re: Double, im: Double) {
    private val re: Double
    private val im: Double
    init {
        this.re = re
        this.im = im
    }

    /**
     * Length of complex number
     * @return length
     */
    fun rho(): Double {return sqrt( re.pow(2) + im.pow(2))}

    /**
     * Argument of complex number
     * @return
     */
    @Suppress("UNUSED")
    fun theta(): Double {return atan2(im, re)}

    /**
     * Complex conjugate
     * @return conjugate
     */
    fun con(): Complex {return Complex(re, -im)}

    /**
     * Complex addition
     * @param other new Complex
     * @return complex sum
     */
    fun add(other: Complex): Complex {return Complex(re + other.re, im + other.im)}

    /**
     * Complex multiply with real value
     *
     * @param a Double value
     * @return multiplication result
     */
    @Suppress("UNUSED")
    fun mul(a: Double): Complex {return Complex(re * a, im * a)}

    /**
     * Complex multiply
     * @param other new Complex
     * @return multiplication result
     */
     fun mul(other: Complex): Complex {return Complex(re * other.re - im * other.im, re * other.im + im * other.re)}

    /**
     * Complex division with real value
     *
     * @param a double value
     * @return division result
     */
    fun div(a: Double): Complex {return Complex(re / a, im / a)}

    /**
     * Complex division
     *
     * @param other
     * @return division result
     */
    fun div(other: Complex): Complex {
        val lengthSquared = other.re.pow(2) + other.im.pow(2)
        return mul(other.con()).div(lengthSquared)
    }
}