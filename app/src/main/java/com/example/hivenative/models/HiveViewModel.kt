package com.example.hivenative.models

import android.util.Log
import androidx.lifecycle.*
import com.example.hivenative.Hive
import com.example.hivenative.PropType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class HiveViewModelFactory(private val name: String) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        HiveViewModel(name) as T
}

class HiveViewModel(private val hiveName: String = "Android Hive") : ViewModel(),
    LifecycleObserver {
    private val hive = Hive(hiveName)

    val properties = hive.propertyList

    var propertyReceived: MutableLiveData<PropType> = MutableLiveData()

    var peers: MutableLiveData<List<String>> = MutableLiveData()
    var peerMessage: MutableLiveData<String> = MutableLiveData()

    private var job: Job? = null

    fun updateProperty(prop_name: String, value: Any?) {
        hive.updateProperty(prop_name, value)
    }

    fun deleteProperty(name: String): Int {
        return hive.deleteProperty(name)
    }

    fun writeToPeer(peerName: String, msg: String) {
        hive.writeToPeer(peerName, msg)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resumeHive() {
        Log.d(javaClass.name, "<<<<<<<<<<<  VIEW MODEL INIT")

        job = viewModelScope.launch {
            withContext(Dispatchers.IO) {

                viewModelScope.launch(Dispatchers.Main) {
                    hive.peerMessages().collect { peerMessage.value = it }
                }
                hive.peersChanged = {
                    viewModelScope.launch(Dispatchers.Main) {
                        peers.value = hive.peersList.filter { it != hiveName }
                    }
                }

                hive.connect("10.0.2.2", 3000).collect {

                    withContext(Dispatchers.Main) { propertyReceived.value = it }
                }
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pauseHive() {
        job?.cancel()
        hive.disconnect()
    }
}