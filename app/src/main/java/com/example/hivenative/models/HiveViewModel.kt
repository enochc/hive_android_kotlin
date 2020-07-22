package com.example.hivenative.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hivenative.Hive
import com.example.hivenative.Peer
import com.example.hivenative.PropType
import com.example.hivenative.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HiveViewModel: ViewModel() {
    val hello:MutableLiveData<String> = MutableLiveData()
    val hiveName: String = "Android Hive"
    private val hive = Hive(hiveName)

    var properties: MutableList<PropType> = mutableListOf()
//    var liveProperties:MutableLiveData<List<PropType>> = MutableLiveData()
    var propertyReceived: MutableLiveData<PropType> = MutableLiveData()

    var peers: MutableLiveData<List<String>> = MutableLiveData()
    var peerMessage: MutableLiveData<String> = MutableLiveData()

    private var job:Job

    fun updateProperty(prop_name: String, value: Any?) {
        hive.updateProperty(prop_name, value)
    }
    fun deleteProperty(name: String): Int {
        return hive.deleteProperty(name)
    }
    fun writeToPeer(peerName: String, msg: String) {
        hive.writeToPeer(peerName, msg)
    }

    init {
        println("<<<<<<<<<<<  VIEW MODEL INIT")
        hello.value = "Hi"

        job = viewModelScope.launch {
            withContext(Dispatchers.IO) {


//                hive.peersChanged = {
//                    peers.value = hive.peersList
//                }

                //            hive.peerMessages().collect{
                //                peerMessage.value = it
                //            }

                // localhost for machine that android emulator is running on
                hive.connect("10.0.2.2", 3000).collect {
                    properties.add(it)
                    withContext(Dispatchers.Main){
//                        liveProperties.value = properties
                        propertyReceived.value = it
                    }

                    //                onPropertyReceived{it}

                    //                // Show the new value on the list
                    //                if (it.doRemove == null) {
                    //                    withContext(Dispatchers.Main) {
                    //                        propertyAdapter.notifyItemInserted(hive.propertyList.size)
                    //                    }
                    //                } else {
                    //                    // Removed from another client and came over the hive stream
                    //                    withContext(Dispatchers.Main) {
                    //                        propertyAdapter.notifyItemRemoved(it.doRemove)
                    //                    }
                    //                }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        job.cancel()
        hive.disconnect()
    }
}