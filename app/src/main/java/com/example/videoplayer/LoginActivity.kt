package com.example.videoplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.videoplayer.databinding.ActivityLoginBinding
import com.example.videoplayer.network.SocketManager
import com.example.videoplayer.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * 登录Activity（迭代14：使用 SocketManager）
 *
 * 功能：
 * 1. 用户输入账号密码
 * 2. 通过 SocketManager 连接服务器并登录
 * 3. 登录成功后跳转到主页
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity 创建")

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val serverIp = binding.etServerIp.text.toString().trim()

            if (username.isEmpty()) {
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (serverIp.isEmpty()) {
                Toast.makeText(this, "请输入服务器IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 禁用登录按钮，防止重复点击
            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = "连接中..."

            // 连接服务器
            connectToServer(serverIp, username, password)
        }

        // 注册按钮
        binding.tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * 连接到服务器（使用 SocketManager）
     */
    private fun connectToServer(serverIp: String, username: String, password: String) {
        Log.d(TAG, "connectToServer: 开始连接 $serverIp, userId=$username")

        //  使用协程调用 SocketManager.connect
        lifecycleScope.launch {
            try {
                //  传递用户名和密码进行验证
                val success = SocketManager.connect(serverIp, username, password)

                // 切换到UI主线程中处理结果
                runOnUiThread {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "登录"
                    if (success) {
                        Log.d(TAG, "connectToServer: 登录成功")

                        //  保存当前用户ID到SessionManager
                        SessionManager.getInstance(this@LoginActivity).setCurrentUserId(username)

                        Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()

                        // 跳转到主页（视频同步移到MainActivity中进行）
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e(TAG, "connectToServer: 登录失败")
                        Toast.makeText(this@LoginActivity, "连接失败，请检查网络", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: SocketManager.LoginException) {
                // 登录失败（密码错误、用户不存在等服务器返回的错误）
                Log.e(TAG, "connectToServer: 登录验证失败 - code=${e.code}, msg=${e.message}")
                runOnUiThread {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "登录"

                    // 根据错误码显示不同的提示信息
                    val errorMsg = when (e.code) {
                        401 -> e.message ?: "用户名或密码错误"
                        500 -> "服务器错误，请稍后重试"
                        else -> e.message ?: "登录失败"
                    }
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "connectToServer: 连接异常", e)
                runOnUiThread {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "登录"
                    Toast.makeText(this@LoginActivity, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Activity 销毁")
    }
}
