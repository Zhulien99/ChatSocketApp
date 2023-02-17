package com.example.chatapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.chatapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var socket: Socket? = null
    private var inRead: BufferedReader? = null
    private var out: PrintWriter? = null
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.signInButton.setOnClickListener {
            userName = binding.usernameEditText.text.toString()
            ConnectToServerTask().execute()
            SendMessageTask().execute(userName)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class ConnectToServerTask : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            try {
                socket = Socket(Constants.hostIp, Constants.port)
                inRead = BufferedReader(InputStreamReader(socket?.getInputStream()))
                out = socket?.getOutputStream()?.let { PrintWriter(it, true) }
                runOnUiThread {
                    binding.txtServerState.text = "Connected to server"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.txtServerState.text = "Error connecting to server"
                }
                return null
            }

            var response = inRead?.readLine()
            if (response == "SUBMITNAME") {
                runOnUiThread {
                }
                out?.println("User")
                response = inRead?.readLine()
                if (response == "NAMEACCEPTED") {
                    runOnUiThread {
                        binding.txtServerState.text = response.toString()
                        MainScope().launch {
                            delay(1000)
                            val intent = Intent(this@LoginActivity,MainActivity::class.java)
                            intent.putExtra("username",userName)
                            startActivity(intent)
                        }
                    }
                }else{
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity,"This Nickname already exist",Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return null
        }
    }
    private inner class SendMessageTask : AsyncTask<String?, Void?, Void?>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: String?): Void? {
            val message = params[0]
            out?.println(message)
            return null
        }
    }
}