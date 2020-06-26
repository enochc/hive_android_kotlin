package com.example.hivenative

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




fun main(args: Array<String>) {

    val fobserver: Observer<String> = object : Observer<String> {
        override fun onComplete() {
            println("All Completed")
        }

        override fun onNext(item: String) {
            println("Next $item")
        }

        override fun onError(e: Throwable) {
            println("Error Occured => ${e.message}")
        }

        override fun onSubscribe(d: Disposable) {
            println("New Subscription ")
        }
    }//Create Observer

    val observable:Observable<String> = Observable.create<String> {//1
        it.onNext("Emitted 1")
        it.onNext("Emitted 2")
        it.onNext("Emitted 3")

        it.onComplete()
    }


    runBlocking {
        launch {
            foo().collect{
                println("hi $it")
            }
        }


        println("Subscribed")

    }
}

fun foo(): Flow<Int> = flow { // sequence builder

    for (i in 1..3) {
//        Thread.sleep(100) // pretend we are computing it
        delay(1000)
        emit(i) // yield next value
    }
}
