package com.fermundev.mysportsscores

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_auth)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //Analytics Events
        val analytics = FirebaseAnalytics.getInstance(this)
        val bundle = Bundle()
        bundle.putString("message", "Integración de Firebase exitosa!")
        analytics.logEvent("InitScreen", bundle)

        //SetUp
        setUp()
    }

    private fun setUp() {
        title = "Autenticación"

        val singUpButton: Button = findViewById(R.id.signUpButton)
        val logInButton: Button = findViewById(R.id.logInButton)
        val emailEditText: EditText = findViewById(R.id.emailEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)

        //SignUp
        singUpButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            emailEditText.text.clear()
                            passwordEditText.text.clear()
                            // Registro exitoso, navegar a la pantalla principal
                            showHome(task.result?.user?.email ?: "", ProviderType.BASIC)
                        } else {
                            // Si el registro falla, mostrar un mensaje al usuario.
                            AlertDialog.Builder(this)
                                .setTitle("Error")
                                .setMessage("Se ha producido un error al registrar el usuario o " +
                                        "ya existe. Inicia Sesión.")
                                .setPositiveButton("Aceptar", null)
                                .show()
                        }
                    }
            } else {
                emptyFieldsError()
            }
        }

        //LogIn with email and password
        logInButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            emailEditText.text.clear()
                            passwordEditText.text.clear()
                            showHome(task.result?.user?.email ?: "", ProviderType.BASIC)
                        } else {
                            // Si el inicio de sesión falla, mostrar un mensaje al usuario.
                            AlertDialog.Builder(this)
                                .setTitle("Error")
                                .setMessage("El email y/o la contraseña son incorrectos o " +
                                        "no existe el usuario. Intenta de nuevo.")
                                .setPositiveButton("Aceptar", null)
                                .show()
                        }
                    }
            } else {
                emptyFieldsError()
            }
        }


    }

    private fun emptyFieldsError() {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage("El email y/o la contraseña no pueden estar vacíos.")
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun showHome(email: String, provider: ProviderType) {
        // Registro exitoso, navegar a la pantalla principal
        val homeIntent = Intent(this, HomeActivity::class.java).apply {
            putExtra("email", email)
            putExtra("provider", provider.name)
        }
        startActivity(homeIntent)
    }
}