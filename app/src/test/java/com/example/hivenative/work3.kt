import java.io.*
import java.math.*
import java.security.*
import java.text.*
import java.util.*
import java.util.concurrent.*
import java.util.function.*
import java.util.regex.*
import java.util.stream.*
import kotlin.collections.*
import kotlin.comparisons.*
import kotlin.io.*
import kotlin.jvm.*
import kotlin.jvm.functions.*
import kotlin.jvm.internal.*
import kotlin.math.round
import kotlin.ranges.*
import kotlin.sequences.*
import kotlin.text.*



/*
 * Complete the 'fizzBuzz' function below.
 *
 * The function accepts INTEGER n as parameter.
 */

fun minimumDivisor(arr: Array<Int>, threshold: Int): Int {
    // Write your code here

    var div = 1

    var newsum = arr.map {
        round((it/div).toDouble())
    }.sum()

    while (newsum > threshold) {
        div +=1
        val newlist = arr.map {
            round((it/div).toDouble())
        }
        newsum = newlist.sum()
    }

    return div
}

fun main(args: Array<String>) {
    val t = arrayOf(
    305709952,
    617901827,
    559066417,
    846642314,
    349430261,
    930100012,
    425149509,
    50710994,
    348655290,
    207497545,
    663923396,
    873283308,
    243509537,
    657804153,
    547001100,
    203492670,
    344685642,
    808597188,
    129005353,
    142684482,
    387013286,
    58302119,
    216770904,
    793436542,
    234999067,
    471073451,
    42602919,
    10272918,
    326437084,
    774854236,
    544470926,
    507360048)
//    val t = ar,rayOf(2,4,5)
    val s = minimumDivisor(t, 612271938)
    println("$s")
}