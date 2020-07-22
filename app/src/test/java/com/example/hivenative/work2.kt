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
import kotlin.ranges.*
import kotlin.sequences.*
import kotlin.text.*



/*
 * Complete the 'fizzBuzz' function below.
 *
 * The function accepts INTEGER n as parameter.
 */

fun fizzBuzz(n: Int): Unit {
    for (x in 1..n) {
        when {
            x%3==0 && x%5 ==0 -> println("FizzBuzz")
            x%3==0 -> println("Fizz")
            x%5==0 -> println("Buzz")
            else -> println("$x")
        }
    }
}

fun main(args: Array<String>) {
    fizzBuzz(6)
}