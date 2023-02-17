package com.example.chatapp

import android.annotation.SuppressLint
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.text.TextUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.adapter.MessageAdapter
import com.example.chatapp.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.broadcast
import java.io.*
import java.net.Socket
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val mMessages: MutableList<Message> = ArrayList()
    private var mUsername: String? = null
    private var socket: Socket? = null
    private var inRead: BufferedReader? = null
    private var out: PrintWriter? = null
    private  var adapter: RecyclerView.Adapter<*>? = null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        ConnectToServerTask().execute()
        getIntentExtras()

//        val socketThread = SocketThread { message ->
//            Log.d("SocketThread", "Received message: $message")
//            addLog(message)
//        }
//        socketThread.start()

        adapter = MessageAdapter(this, mMessages)
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = adapter

        binding.sendButton.setOnClickListener {
            attemptSend()
        }
    }


    private fun getIntentExtras(){

        mUsername = intent!!.getStringExtra("username")
        addLog(getString(R.string.message_welcome))
    }

    private fun scrollToBottom() {
        binding.rvMessages.scrollToPosition(adapter?.itemCount?.minus(1) ?: 0)
    }

    private fun attemptSend() {

        if (null == mUsername) return
        if (!socket!!.isConnected) return
        val message = binding.messageEditText.text.toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(message)) {
            binding.messageEditText.requestFocus()
            return
        }
        binding.messageEditText.setText("")
        addMessage(mUsername!!, message)

        // perform the sending message attempt.
        SendMessageTask().execute(message)
    }

    private fun addLog(message: String) {
        mMessages.add(Message.Builder(Message.TYPE_LOG).message(message).build())
        adapter?.notifyItemInserted(mMessages.size - 1)
        scrollToBottom()
    }

    private fun addMessage(username: String, message: String) {

        mMessages.add(Message.Builder(Message.TYPE_MESSAGE).username(username).message(message).build())
        adapter?.notifyItemInserted(mMessages.size - 1)
        scrollToBottom()
    }

    private fun addTyping(username: String) {
        mMessages.add(Message.Builder(Message.TYPE_ACTION).username(username).build())
        adapter?.notifyItemInserted(mMessages.size - 1)
        scrollToBottom()
    }

    private fun removeTyping(username: String) {

        for (i in mMessages.indices.reversed()) {
            val message = mMessages[i]
            if (message.type == Message.TYPE_ACTION && message.username == username) {
                mMessages.removeAt(i)
                adapter?.notifyItemRemoved(i)
            }
        }
    }

    fun listenForServerMessages() {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                val message = withContext(Dispatchers.IO) { receiveMessageFromServer() }
                withContext(Dispatchers.Main) {
                    addLog(message)
                }
            }
        }
    }

    fun receiveMessageFromServer(): String {
        val inputStream = socket?.getInputStream()
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        return bufferedReader.readLine()
    }

    @SuppressLint("StaticFieldLeak")
    private inner class ConnectToServerTask : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            try {
                socket = Socket(Constants.hostIp, Constants.port)
                inRead = BufferedReader(InputStreamReader(socket?.getInputStream()))
                out = socket?.getOutputStream()?.let { PrintWriter(it, true) }
                runOnUiThread {
                    mUsername?.let { addTyping(it) }
                }

            } catch (e: Exception) {
                runOnUiThread {
                }
                return null
            }

            var response = inRead?.readLine()
            if (response == "SUBMITNAME") {
                out?.println("get Users")
                response = inRead?.readLine()
                 runOnUiThread {
                    mUsername?.let { removeTyping(it) }
                    addLog("Users in Chat $response")
                }
            }
            return null
        }
    }

    private inner class SendMessageTask : AsyncTask<String?, Void?, Void?>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: String?): Void? {
            val message = params[0]
            try {

                socket = Socket(Constants.hostIp, Constants.port)
                inRead = BufferedReader(InputStreamReader(socket?.getInputStream()))
                out = socket?.getOutputStream()?.let { PrintWriter(it, true) }

                MainScope().launch(Dispatchers.IO){
                    var counter = 0
                    while (true){
                        var response: String
                        withContext(Dispatchers.IO){
                            response = inRead?.readLine().toString()
                            out?.println(mUsername)
                            out?.println(message)
                            response = inRead?.readLine().toString()
                            out?.println(message)
                            response = inRead?.readLine().toString()
                        }
                        withContext(Dispatchers.Main){
                            mUsername?.let { removeTyping(it) }
                            counter++
                        }
                        if (counter==2){
                            val pattern = "MESSAGE (.*): (.*)".toRegex()
                            val matchResult = pattern.matchEntire(response)
                            runOnUiThread {
                                matchResult?.groupValues?.get(1)
                                    ?.let { addMessage(it, matchResult.groupValues.get(2)) }
                            }
                            counter = 0
                            break
                        }
                    }
                }
                return null

            } catch (e: Exception) {
                runOnUiThread {
                }
                return null
            }
        }
    }
}