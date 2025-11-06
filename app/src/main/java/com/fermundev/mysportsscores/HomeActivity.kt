package com.fermundev.mysportsscores

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
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
import com.google.firebase.crashlytics.FirebaseCrashlytics

enum class ProviderType{
    BASIC,
    GOOGLE
}

class HomeActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityHomeBinding
    private lateinit var navController: NavController
    private var backPressedTime: Long = 0
    private var isFabMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarHome.toolbar)

        val bundle = intent.extras
        val email = bundle?.getString("email")
        val provider = bundle?.getString("provider")
        val idUser = bundle?.getString("idUser")

        //SetUp
        setUp(email ?:"", provider ?:"")

        //Guardado de Sesión
        saveSession(email, provider)

        // Speed Dial FAB Logic
        setupFabMenu()

        //Registro de errores de navegación
        getErrorInNavigation(email, provider, idUser)

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

    private fun getErrorInNavigation(email: String?, provider: String?, idUser: String?) {
        email?.let { FirebaseCrashlytics.getInstance().setUserId(it) }
        provider?.let { FirebaseCrashlytics.getInstance().setCustomKey("provider", it) }
        idUser?.let { FirebaseCrashlytics.getInstance().setCustomKey("idUser", it) }
    }

    private fun setupFabMenu() {
        // Inicialmente los elementos están ocultos y no son clicables
        closeFabMenu()

        binding.appBarHome.fab?.setOnClickListener {
            if (!isFabMenuOpen) {
                showFabMenu()
            } else {
                closeFabMenu()
            }
        }

        // Listeners para los mini-fabs
        binding.appBarHome.fabNewSport?.setOnClickListener {
            Toast.makeText(this, "Crear nuevo Deporte", Toast.LENGTH_SHORT).show()
            closeFabMenu()
        }
        binding.appBarHome.fabNewGroup?.setOnClickListener {
            Toast.makeText(this, "Crear nuevo Grupo", Toast.LENGTH_SHORT).show()
            closeFabMenu()
        }
        binding.appBarHome.fabAddResult?.setOnClickListener {
            Toast.makeText(this, "Agregar nuevo resultado", Toast.LENGTH_SHORT).show()
            closeFabMenu()
        }
        binding.appBarHome.fabInviteFriend?.setOnClickListener {
            Toast.makeText(this, "Invitar a un amigo", Toast.LENGTH_SHORT).show()
            closeFabMenu()
        }
        binding.appBarHome.fabTakePhoto?.setOnClickListener {
            Toast.makeText(this, "Tomar una foto", Toast.LENGTH_SHORT).show()
            closeFabMenu()
        }
    }

    private fun showFabMenu() {
        isFabMenuOpen = true

        // Hacer visibles y clicables los mini-fabs y etiquetas
        binding.appBarHome.fabNewSport?.visibility = View.VISIBLE
        binding.appBarHome.fabNewGroup?.visibility = View.VISIBLE
        binding.appBarHome.fabAddResult?.visibility = View.VISIBLE
        binding.appBarHome.fabInviteFriend?.visibility = View.VISIBLE
        binding.appBarHome.fabTakePhoto?.visibility = View.VISIBLE
        binding.appBarHome.labelNewSport?.visibility = View.VISIBLE
        binding.appBarHome.labelNewGroup?.visibility = View.VISIBLE
        binding.appBarHome.labelAddResult?.visibility = View.VISIBLE
        binding.appBarHome.labelInviteFriend?.visibility = View.VISIBLE
        binding.appBarHome.labelTakePhoto?.visibility = View.VISIBLE

        binding.appBarHome.fabNewSport?.isClickable = true
        binding.appBarHome.fabNewGroup?.isClickable = true
        binding.appBarHome.fabAddResult?.isClickable = true
        binding.appBarHome.fabInviteFriend?.isClickable = true
        binding.appBarHome.fabTakePhoto?.isClickable = true

        // Animar rotación del FAB principal
        binding.appBarHome.fab?.animate()?.rotation(45f)

        // Animar la aparición de los mini-fabs y etiquetas
        binding.appBarHome.fabNewSport?.animate()?.translationY(-resources.getDimension(R.dimen.standard_10))
        binding.appBarHome.labelNewSport?.animate()?.translationY(-resources.getDimension(R.dimen.standard_10))

        binding.appBarHome.fabNewGroup?.animate()?.translationY(-resources.getDimension(R.dimen.standard_20))
        binding.appBarHome.labelNewGroup?.animate()?.translationY(-resources.getDimension(R.dimen.standard_20))

        binding.appBarHome.fabAddResult?.animate()?.translationY(-resources.getDimension(R.dimen.standard_30))
        binding.appBarHome.labelAddResult?.animate()?.translationY(-resources.getDimension(R.dimen.standard_30))

        binding.appBarHome.fabInviteFriend?.animate()?.translationY(-resources.getDimension(R.dimen.standard_40))
        binding.appBarHome.labelInviteFriend?.animate()?.translationY(-resources.getDimension(R.dimen.standard_40))

        binding.appBarHome.fabTakePhoto?.animate()?.translationY(-resources.getDimension(R.dimen.standard_50))
        binding.appBarHome.labelTakePhoto?.animate()?.translationY(-resources.getDimension(R.dimen.standard_50))
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false

        // Animar rotación del FAB principal para volver a la normalidad
        binding.appBarHome.fab?.animate()?.rotation(0f)

        // Animar el repliegue de los mini-fabs y etiquetas
        binding.appBarHome.fabNewSport?.animate()?.translationY(0f)
        binding.appBarHome.labelNewSport?.animate()?.translationY(0f)

        binding.appBarHome.fabNewGroup?.animate()?.translationY(0f)
        binding.appBarHome.labelNewGroup?.animate()?.translationY(0f)

        binding.appBarHome.fabAddResult?.animate()?.translationY(0f)
        binding.appBarHome.labelAddResult?.animate()?.translationY(0f)

        binding.appBarHome.fabInviteFriend?.animate()?.translationY(0f)
        binding.appBarHome.labelInviteFriend?.animate()?.translationY(0f)

        binding.appBarHome.fabTakePhoto?.animate()?.translationY(0f)
        binding.appBarHome.labelTakePhoto?.animate()?.translationY(0f)?.withEndAction {
            if (!isFabMenuOpen) {
                // Ocultar los elementos cuando la animación termine
                binding.appBarHome.fabNewSport?.visibility = View.INVISIBLE
                binding.appBarHome.fabNewGroup?.visibility = View.INVISIBLE
                binding.appBarHome.fabAddResult?.visibility = View.INVISIBLE
                binding.appBarHome.fabInviteFriend?.visibility = View.INVISIBLE
                binding.appBarHome.fabTakePhoto?.visibility = View.INVISIBLE
                binding.appBarHome.labelNewSport?.visibility = View.INVISIBLE
                binding.appBarHome.labelNewGroup?.visibility = View.INVISIBLE
                binding.appBarHome.labelAddResult?.visibility = View.INVISIBLE
                binding.appBarHome.labelInviteFriend?.visibility = View.INVISIBLE
                binding.appBarHome.labelTakePhoto?.visibility = View.INVISIBLE

                // Hacerlos no clicables
                binding.appBarHome.fabNewSport?.isClickable = false
                binding.appBarHome.fabNewGroup?.isClickable = false
                binding.appBarHome.fabAddResult?.isClickable = false
                binding.appBarHome.fabInviteFriend?.isClickable = false
                binding.appBarHome.fabTakePhoto?.isClickable = false
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
                        val prefs = getSharedPreferences(getString(R.string.prefs_file), Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
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