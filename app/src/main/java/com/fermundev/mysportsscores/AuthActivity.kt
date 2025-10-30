package com.fermundev.mysportsscores

import android.app.ComponentCaller
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

private const val GOOGLE_SIGN_IN = 100

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
        session()
    }

    override fun onStart() {
        super.onStart()
        val authLayout = findViewById<View>(R.id.authLayout)
        authLayout.visibility = View.VISIBLE
    }

    private fun session() {
        val prefs = getSharedPreferences(getString(R.string.prefs_file), MODE_PRIVATE)
        val email = prefs.getString("email", null)
        val provider = prefs.getString("provider", null)

        if (email != null && provider != null) {
            val authLayout = findViewById<View>(R.id.authLayout)
            authLayout.visibility = View.INVISIBLE
            showHome(email, ProviderType.valueOf(provider))
        }
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

        //Inicio de Sesión con Google
        val googleButton: Button = findViewById(R.id.googleButton)
        googleButton.setOnClickListener {
            val googleConfig = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            val googleClient = GoogleSignIn.getClient(this, googleConfig)
            googleClient.signOut()
            startActivityForResult(googleClient.signInIntent, GOOGLE_SIGN_IN)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(ApiException::class.java)

                if (account != null) {
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential)

                    if (task.isSuccessful) {
                        showHome(account.email ?: "", ProviderType.GOOGLE)
                    } else {
                        // Si el inicio de sesión falla, mostrar un mensaje al usuario.
                        AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("Error al ingresar con Google. Intenta de nuevo.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                }
            } catch (e: ApiException) {
                AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage("Error al ingresar con Google. Intenta de nuevo.")
                    .setPositiveButton("Aceptar", null)
                    .show()
            }


        }
    }
}