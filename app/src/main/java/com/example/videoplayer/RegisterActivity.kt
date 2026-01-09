package com.example.videoplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.videoplayer.databinding.ActivityRegisterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

// 注册界面
class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RegisterActivity"
        private const val SERVER_PORT = 8888
    }

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Back button
        binding.ivBack.setOnClickListener {
            finish()
        }

        // Register button
        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            val serverIp = binding.etServerIp.text.toString().trim()

            when {
                username.isEmpty() -> {
                    Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
                }
                username.length < 3 || username.length > 20 -> {
                    Toast.makeText(this, "用户名长度必须在3-20个字符之间", Toast.LENGTH_SHORT).show()
                }
                password.isEmpty() -> {
                    Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                }
                password.length < 6 -> {
                    Toast.makeText(this, "密码至少需要6个字符", Toast.LENGTH_SHORT).show()
                }
                confirmPassword != password -> {
                    Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
                }
                serverIp.isEmpty() -> {
                    Toast.makeText(this, "请输入服务器IP", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    performRegister(username, password, serverIp)
                }
            }
        }

        // Login link
        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    /**
     * 注册
     */
    private fun performRegister(username: String, password: String, serverIp: String) {
        Log.d(TAG, "performRegister: username=$username, serverIp=$serverIp")

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    registerToServer(username, password, serverIp)
                }

                if (result) {
                    Toast.makeText(this@RegisterActivity, "注册成功！请登录", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@RegisterActivity, "注册失败，用户名可能已存在", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "performRegister: 注册失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "无法连接到服务器: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 向服务器发送注册Socket
     */
    private fun registerToServer(username: String, password: String, serverIp: String): Boolean {
        var socket: Socket? = null
        try {
            socket = Socket(serverIp, SERVER_PORT)
            socket.soTimeout = 5000

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            // 发送 REGISTER
            writer.println("REGISTER:$username:$password")
            Log.d(TAG, "registerToServer: 发送注册请求 - username=$username")

            // Read response
            val response = reader.readLine()
            Log.d(TAG, "registerToServer: 收到响应 - $response")

            return response == "REGISTER_SUCCESS"

        } finally {
            socket?.close()
        }
    }
}
