package com.example.hivenative

import androidx.core.content.contentValuesOf
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.toFlowable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.cos



class Solution {
    val wins = listOf(
        intArrayOf(1,2,3),
        intArrayOf(4,5,6),
        intArrayOf(7,8,9),

        intArrayOf(1,4,7),
        intArrayOf(2,5,8),
        intArrayOf(3,6,9),

        intArrayOf(1,5,9),
        intArrayOf(7,5,3)
    )

    fun won(array:List<Int>): Boolean {
        for  (w1 in wins){
            if(array.contains(w1[0]) && array.contains(w1[1]) && array.contains(w1[2])){
                return true
            }
        }
        return false
    }

    fun tictactoe(moves: Array<IntArray>): String {
        val plainMoves = moves.map{
            when {
                it.contentEquals(intArrayOf(0,0)) -> 1
                it.contentEquals(intArrayOf(0,1)) -> 2
                it.contentEquals(intArrayOf(0,2)) -> 3
                it.contentEquals(intArrayOf(1,0)) -> 4
                it.contentEquals(intArrayOf(1,1)) -> 5
                it.contentEquals(intArrayOf(1,2)) -> 6
                it.contentEquals(intArrayOf(2,0)) -> 7
                it.contentEquals(intArrayOf(2,1)) -> 8
                else -> 9
            }
        }
        val aMoves = plainMoves.filterIndexed{index, i ->
            index % 2 == 0
        }
        val bMoves = plainMoves.filterIndexed { index, i ->
            index % 2 != 0
        }

        return if(won(aMoves)){
            "A"
        } else if(won (bMoves)){
            "B"
        } else if(plainMoves.size <9){
            "Pending"
        } else {
            "Draw"
        }
    }
}


fun main(args: Array<String>) {
    // [[0,0],[1,1],[2,0],[1,0],[1,2],[2,1],[0,1],[0,2],[2,2]]
    val moves = arrayOf( intArrayOf(0,0), intArrayOf(1,1), intArrayOf(2,0), intArrayOf(1,0), intArrayOf(1,2),
        intArrayOf(2,1), intArrayOf(0,1), intArrayOf(0,2), intArrayOf(2,2))
        println("<< ${Solution().tictactoe(moves)}")

}


