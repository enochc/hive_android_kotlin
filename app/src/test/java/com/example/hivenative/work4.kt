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
class Event(val teamName:String, val empName:String, val time:String, val type:Char? = null){


    override fun toString():String{
        return "$teamName $empName $time $type"
    }

    //todo fix this
    fun getTime():Int {
        return time.toInt()
    }
}

fun getEventsOrder(team1: String, team2: String, events1: Array<String>, events2: Array<String>): Array<String> {
    // Write your code here
    val events:MutableList<Event> = mutableListOf()
    // spit out time and type value here
//    events.add(Event(team1, events1[0]))
//    events.add(Event(team1, events2[0]))
//    events.add(Event(team2, events1[1]))
//    events.add(Event(team2, events2[1]))

    events.sortBy {
        it.getTime() }

    return events.map { it.toString() }.toTypedArray()
}

fun main(args: Array<String>) {

    val s = getEventsOrder("abc", "cba",
        arrayOf("mo sa 45+2 Y", "a 13 G"),
        arrayOf("d 23 S f", "z 46 G"))
    println("$s")
}