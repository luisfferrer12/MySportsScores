package com.fermundev.mysportsscores

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import com.fermundev.mysportsscores.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import androidx.core.content.edit

enum class ProviderType{
    BASIC,
    GOOGLE
}

class HomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityHomeBinding
    private lateinit var navController: NavController
    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarHome.toolbar)

        val bundle = intent.extras
        val email = bundle?.getString("email")
        val provider = bundle?.getString("provider")
        //SetUp
        setUp(email ?:"", provider ?:"")

        //Guardado de Sesión
        saveSession(email, provider)


        binding.appBarHome.fab?.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_home) as NavHostFragment?)!!
        navController = navHostFragment.navController

        // CORRECCIÓN: Se elimina nav_settings de las pantallas de nivel superior
        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow
                ),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        binding.appBarHome.contentHome.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        // Lógica corregida para el botón "Atrás"
        onBackPressedDispatcher.addCallback(this) {
            // ¿Estamos en la pantalla de inicio del gráfico de navegación?
            if (navController.currentDestination?.id == navController.graph.startDestinationId) {
                // Sí: aplicar lógica de doble pulsación para salir
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(baseContext, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show()
                }
                backPressedTime = System.currentTimeMillis()
            } else {
                // No: navegar hacia atrás normalmente (ej. de Settings a Home)
                navController.navigateUp()
            }
        }
    }

    private fun saveSession(email: String?, provider: String?) {
        getSharedPreferences(
            getString(R.string.prefs_file),
            MODE_PRIVATE
        ).edit {
            putString("email", email)
            putString("provider", provider)
        }
    }

    private fun setUp(email: String, provider: String) {
        title = "Inicio"

        println("EMAIL ----> $email")
        println("PROVIDER ----> $provider")

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            menuInflater.inflate(R.menu.overflow, menu)
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                navController.navigate(R.id.nav_settings)
                return true
            }
            R.id.logOutButton -> {
                AlertDialog.Builder(this)
                    .setTitle("Cerrar Sesión")
                    .setMessage("¿Seguro que deseas cerrar sesión?")
                    .setPositiveButton("Cerrar") { _, _ ->
                        FirebaseAuth.getInstance().signOut()
                        val prefs = getSharedPreferences(getString(R.string.prefs_file), MODE_PRIVATE)
                        prefs.edit { clear() }
                        val intent = Intent(this, AuthActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}