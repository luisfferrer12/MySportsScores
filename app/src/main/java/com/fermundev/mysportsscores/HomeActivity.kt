package com.fermundev.mysportsscores

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.fermundev.mysportsscores.databinding.ActivityHomeBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.remoteConfig
import java.util.UUID

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
    private val db = FirebaseFirestore.getInstance()
    private var currentUserEmail: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permisos para notificaciones concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permisos para notificaciones denegados", Toast.LENGTH_SHORT).show()
            if (currentUserEmail != null) {
                db.collection("users").document(currentUserEmail!!).update("notificaciones", false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarHome.toolbar)

        askNotificationPermission()

        val bundle = intent.extras
        currentUserEmail = bundle?.getString("email")
        val provider = bundle?.getString("provider")

        setUp(currentUserEmail ?:"", provider ?:"")
        saveSession(currentUserEmail, provider)
        setupFabMenu()
        getErrorInNavigation(currentUserEmail, provider, bundle?.getString("idUser"))

        val navHostFragment = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_home) as NavHostFragment?)!!
        navController = navHostFragment.navController

        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_home, R.id.nav_results, R.id.nav_gallery), binding.drawerLayout)
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        binding.appBarHome.contentHome.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_home, R.id.nav_results, R.id.nav_gallery))
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        onBackPressedDispatcher.addCallback(this) {
            if (navController.currentDestination?.id == navController.graph.startDestinationId) {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(baseContext, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show()
                }
                backPressedTime = System.currentTimeMillis()
            } else {
                navController.navigateUp()
            }
        }
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
            R.id.my_sports -> {
                showSelectSportDialog()
                return true
            }
            R.id.errorButton -> {
                throw RuntimeException("Test Crash") // Force a crash
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSelectSportDialog() {
        if (currentUserEmail == null) return

        val userDocRef = db.collection("users").document(currentUserEmail!!)
        userDocRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val sportsList = document.get("listaDeportes") as? List<String> ?: emptyList()
                val activeSport = document.getString("grupoActivo")

                if (sportsList.isEmpty()) {
                    Toast.makeText(this, "Aún no has creado ningún deporte", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val sportsArray = sportsList.toTypedArray()
                var checkedItem = sportsList.indexOf(activeSport)
                if (checkedItem == -1) checkedItem = 0 

                AlertDialog.Builder(this)
                    .setTitle("Selecciona tu Deporte Activo")
                    .setSingleChoiceItems(sportsArray, checkedItem) { dialog, which ->
                        val selectedSport = sportsArray[which]
                        updateActiveSport(selectedSport)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun updateActiveSport(sportName: String) {
        if (currentUserEmail == null) return
        db.collection("users").document(currentUserEmail!!)
            .update("grupoActivo", sportName)
            .addOnSuccessListener {
                Toast.makeText(this, "'$sportName' es ahora tu deporte activo", Toast.LENGTH_SHORT).show()
                navController.navigate(R.id.nav_home)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al actualizar el deporte: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupFabMenu() {
        binding.appBarHome.fabOverlay?.setOnClickListener { closeFabMenu() }
        closeFabMenu()
        binding.appBarHome.fab?.setOnClickListener {
            if (!isFabMenuOpen) showFabMenu() else closeFabMenu()
        }
        binding.appBarHome.fabNewSport?.setOnClickListener {
            showCreateSportDialog()
            closeFabMenu()
        }
        // ... otros listeners
    }

    private fun showCreateSportDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Crear Nuevo Deporte")

        val container = FrameLayout(this)
        val input = EditText(this)
        input.hint = "Nombre del Deporte"
        container.addView(input)

        val padding = (20 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, 0, padding, 0)
        builder.setView(container)

        builder.setPositiveButton("Crear") { _, _ ->
            val sportName = input.text.toString().trim()
            if (sportName.isNotEmpty()) {
                createSportInDatabase(sportName)
            } else {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun createSportInDatabase(sportName: String) {
        if (currentUserEmail == null) {
            Toast.makeText(this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show()
            return
        }

        val userDocRef = db.collection("users").document(currentUserEmail!!)
        val sportDocRef = userDocRef.collection("Deportes").document(sportName)
        val sportId = UUID.randomUUID().toString()

        db.runBatch { batch ->
            batch.set(sportDocRef, hashMapOf("idDeporte" to sportId))
            batch.update(userDocRef, "listaDeportes", FieldValue.arrayUnion(sportName))
        }.addOnSuccessListener {
            Toast.makeText(this, "Deporte '$sportName' creado con éxito", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error al crear el deporte: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                 requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun getErrorInNavigation(email: String?, provider: String?, idUser: String?) {
        email?.let { FirebaseCrashlytics.getInstance().setUserId(it) }
        provider?.let { FirebaseCrashlytics.getInstance().setCustomKey("provider", it) }
        idUser?.let { FirebaseCrashlytics.getInstance().setCustomKey("idUser", it) }
    }

    private fun showFabMenu() {
        isFabMenuOpen = true
        binding.appBarHome.fabOverlay?.visibility = View.VISIBLE
        binding.appBarHome.fabOverlay?.isClickable = true
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
        binding.appBarHome.fab?.animate()?.rotation(45f)
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
        binding.appBarHome.fabOverlay?.visibility = View.GONE
        binding.appBarHome.fabOverlay?.isClickable = false
        binding.appBarHome.fab?.animate()?.rotation(0f)
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
                binding.appBarHome.fabNewSport?.isClickable = false
                binding.appBarHome.fabNewGroup?.isClickable = false
                binding.appBarHome.fabAddResult?.isClickable = false
                binding.appBarHome.fabInviteFriend?.isClickable = false
                binding.appBarHome.fabTakePhoto?.isClickable = false
            }
        }
    }

    private fun saveSession(email: String?, provider: String?) {
        getSharedPreferences(getString(R.string.prefs_file), MODE_PRIVATE).edit { putString("email", email); putString("provider", provider) }
    }

    private fun setUp(email: String, provider: String) {
        title = "Inicio"
        println("EMAIL ----> $email")
        println("PROVIDER ----> $provider")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            menuInflater.inflate(R.menu.overflow, menu)
            val errorButton = menu.findItem(R.id.errorButton)
            errorButton.isVisible = false
            Firebase.remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val showErrorButton = Firebase.remoteConfig.getBoolean("show_error_button")
                    val newButtonText = Firebase.remoteConfig.getString("error_button_text")
                    if (showErrorButton) {
                        errorButton.isVisible = true
                    }
                    errorButton.title = newButtonText
                }
            }
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
