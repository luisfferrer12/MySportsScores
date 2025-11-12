package com.fermundev.mysportsscores

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.fermundev.mysportsscores.databinding.ActivityHomeBinding
import com.fermundev.mysportsscores.ui.gallery.GalleryFragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.storage.FirebaseStorage
import java.io.File
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
    private var sportForNewPhoto: String? = null
    private var latestTmpUri: Uri? = null

    // --- ActivityResultLaunchers ---
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.notifications_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.notifications_permission_denied), Toast.LENGTH_SHORT).show()
            currentUserEmail?.let { email ->
                db.collection("users").document(email).update("notificaciones", false)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { isSuccess: Boolean ->
        if (isSuccess) {
            latestTmpUri?.let { uri ->
                sportForNewPhoto?.let { sportName ->
                    uploadImageToGallery(uri, sportName)
                }
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

        setUp(currentUserEmail ?: "", provider ?: "")
        saveSession(currentUserEmail, provider)
        setupFabMenu()
        getErrorInNavigation(currentUserEmail, provider, bundle?.getString("idUser"))

        val navHostFragment = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_home) as NavHostFragment?)!!
        navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.appBarHome.fab?.let {
                if (destination.id == R.id.nav_players) {
                    it.visibility = View.GONE
                } else {
                    it.visibility = View.VISIBLE
                }
            }
        }

        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_home, R.id.nav_results, R.id.nav_players, R.id.nav_gallery), binding.drawerLayout)
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        binding.appBarHome.contentHome.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_home, R.id.nav_results, R.id.nav_players, R.id.nav_gallery))
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        onBackPressedDispatcher.addCallback(this) {
            if (navController.currentDestination?.id == navController.graph.startDestinationId) {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(baseContext, getString(R.string.exit_prompt), Toast.LENGTH_SHORT).show()
                }
                backPressedTime = System.currentTimeMillis()
            } else {
                navController.navigateUp()
            }
        }
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                navController.navigate(R.id.nav_settings)
                return true
            }
            R.id.logOutButton -> { 
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_title_logout))
                    .setMessage(getString(R.string.dialog_message_logout))
                    .setPositiveButton(getString(R.string.action_close)) { _, _ ->
                        FirebaseAuth.getInstance().signOut()
                        val prefs = getSharedPreferences(getString(R.string.prefs_file), MODE_PRIVATE)
                        prefs.edit { clear() }
                        val intent = Intent(this, AuthActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    .setNegativeButton(getString(R.string.action_cancel), null)
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
        binding.appBarHome.fabAddResult?.setOnClickListener {
            showAddResultDialog()
            closeFabMenu()
        }
        binding.appBarHome.fabTakePhoto?.setOnClickListener {
            startPhotoProcess()
            closeFabMenu()
        }
    }

    private fun startPhotoProcess() {
        currentUserEmail?.let { email ->
            db.collection("users").document(email).get().addOnSuccessListener { userDoc ->
                val activeSport = userDoc.getString("grupoActivo")
                if (activeSport.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.error_no_active_sport_photo), Toast.LENGTH_LONG).show()
                } else {
                    sportForNewPhoto = activeSport
                    handleTakePictureClick()
                }
            }
        }
    }

    private fun handleTakePictureClick() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.camera_permission_title))
                    .setMessage(getString(R.string.camera_permission_message))
                    .setPositiveButton(getString(android.R.string.ok)) { _, _ -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .show()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        getTmpFileUri().let { uri ->
            latestTmpUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    private fun getTmpFileUri(): Uri {
        val imagePath = File(cacheDir, "images")
        imagePath.mkdirs()
        val tmpFile = File.createTempFile("tmp_image_", ".jpg", imagePath)
        return FileProvider.getUriForFile(applicationContext, "${packageName}.provider", tmpFile)
    }

    private fun uploadImageToGallery(uri: Uri, sportName: String) {
        currentUserEmail?.let { email ->
            val storageRef = FirebaseStorage.getInstance().reference
            val imageFileName = "${UUID.randomUUID()}.jpg"
            val galleryRef = storageRef.child("MySportsScores/$email/gallery/$sportName/$imageFileName")

            Toast.makeText(this, getString(R.string.uploading_image), Toast.LENGTH_SHORT).show()
            galleryRef.putFile(uri)
                .addOnSuccessListener { 
                    Toast.makeText(this, getString(R.string.success_photo_saved, sportName), Toast.LENGTH_LONG).show()
                    
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_home)
                    val currentFragment = navHostFragment?.childFragmentManager?.fragments?.get(0)
                    if (currentFragment is GalleryFragment) {
                        currentFragment.refreshGallery()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, getString(R.string.error_uploading_image, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showAddResultDialog() {
        currentUserEmail?.let { email ->
            val userDocRef = db.collection("users").document(email)
            userDocRef.get().addOnSuccessListener { document ->
                val activeSport = document.getString("grupoActivo")
                if (activeSport.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.error_select_sport_first), Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val sportDocRef = userDocRef.collection("Deportes").document(activeSport)
                sportDocRef.get().addOnSuccessListener { sportDoc ->
                    val players = sportDoc.get("jugadores") as? List<String> ?: emptyList()
                    if (players.size < 2) {
                        Toast.makeText(this, getString(R.string.error_need_at_least_two_players), Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_result, null)
                    val player1Spinner = dialogView.findViewById<Spinner>(R.id.player1_spinner)
                    val player2Spinner = dialogView.findViewById<Spinner>(R.id.player2_spinner)
                    val player1Points = dialogView.findViewById<EditText>(R.id.player1_points_edittext)
                    val player2Points = dialogView.findViewById<EditText>(R.id.player2_points_edittext)
                    val saveButton = dialogView.findViewById<Button>(R.id.save_button)
                    val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)

                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, players)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    player1Spinner.adapter = adapter
                    player2Spinner.adapter = adapter

                    val dialog = AlertDialog.Builder(this)
                        .setView(dialogView)
                        .setTitle(getString(R.string.dialog_title_add_result))
                        .create()

                    saveButton.setOnClickListener {
                        val p1 = player1Spinner.selectedItem.toString()
                        val p2 = player2Spinner.selectedItem.toString()
                        val p1PointsText = player1Points.text.toString()
                        val p2PointsText = player2Points.text.toString()

                        if (p1.isNotEmpty() && p2.isNotEmpty() && p1PointsText.isNotEmpty() && p2PointsText.isNotEmpty() && p1 != p2) {
                            saveNewResult(activeSport, p1, p2, p1PointsText.toInt(), p2PointsText.toInt())
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this, getString(R.string.error_add_result_validation), Toast.LENGTH_LONG).show()
                        }
                    }

                    cancelButton.setOnClickListener { dialog.dismiss() }
                    dialog.show()
                }
            }
        }
    }

    private fun saveNewResult(sportName: String, p1: String, p2: String, p1Points: Int, p2Points: Int) {
        currentUserEmail?.let { email ->
            val sportDocRef = db.collection("users").document(email).collection("Deportes").document(sportName)
            val resultsColRef = sportDocRef.collection("Resultados")

            resultsColRef.get().addOnSuccessListener { querySnapshot ->
                val matchNumber = querySnapshot.size() + 1
                val resultId = UUID.randomUUID().toString()
                val newResultDoc = resultsColRef.document("partido N°$matchNumber")

                db.runBatch { batch ->
                    val (winner, loser) = if (p1Points > p2Points) p1 to p2 else p2 to p1

                    batch.update(sportDocRef, "ranking.$winner", FieldValue.increment(3))
                    batch.update(sportDocRef, "ranking.$loser", FieldValue.increment(1))
                    
                    val resultData = hashMapOf(
                        "jugador1" to p1,
                        "jugador2" to p2,
                        "puntosJugador1" to p1Points,
                        "puntosJugador2" to p2Points,
                        "timestamp" to Timestamp.now(),
                        "uid" to resultId
                    )
                    batch.set(newResultDoc, resultData)

                }.addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.success_result_saved), Toast.LENGTH_SHORT).show()
                    navController.navigate(R.id.nav_home)
                }.addOnFailureListener { e ->
                    Toast.makeText(this, getString(R.string.error_saving_result, e.message), Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.error_counting_matches, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createSportInDatabase(sportName: String) {
        currentUserEmail?.let { email ->
            val userDocRef = db.collection("users").document(email)
            userDocRef.get().addOnSuccessListener { userDoc ->
                val sportsList = userDoc.get("listaDeportes") as? List<String> ?: emptyList()
                val isFirstSport = sportsList.isEmpty()
                val nickName = userDoc.getString("nickName")

                val sportDocRef = userDocRef.collection("Deportes").document(sportName)
                
                val sportData: HashMap<String, Any>
                if (isFirstSport && nickName != null && nickName.isNotEmpty()) {
                    sportData = hashMapOf(
                        "idDeporte" to UUID.randomUUID().toString(),
                        "jugadores" to listOf(nickName),
                        "ranking" to mapOf(nickName to 0L)
                    )
                } else {
                    sportData = hashMapOf(
                        "idDeporte" to UUID.randomUUID().toString(),
                        "jugadores" to emptyList<String>(),
                        "ranking" to emptyMap<String, Long>()
                    )
                }

                db.runBatch { batch ->
                    batch.set(sportDocRef, sportData)
                    batch.update(userDocRef, "listaDeportes", FieldValue.arrayUnion(sportName))
                    if (isFirstSport) {
                        batch.update(userDocRef, "grupoActivo", sportName)
                    }
                }.addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.success_sport_created, sportName), Toast.LENGTH_SHORT).show()
                    if (isFirstSport) {
                        navController.navigate(R.id.nav_home)
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, getString(R.string.error_creating_sport, e.message), Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener { 
                Toast.makeText(this, getString(R.string.error_verifying_sports), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showSelectSportDialog() {
        currentUserEmail?.let { email ->
            val userDocRef = db.collection("users").document(email)
            userDocRef.get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val sportsList = document.get("listaDeportes") as? List<String> ?: emptyList()
                    val activeSport = document.getString("grupoActivo")

                    if (sportsList.isEmpty()) {
                        Toast.makeText(this, getString(R.string.error_no_sports_created), Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val sportsArray = sportsList.toTypedArray()
                    var checkedItem = sportsList.indexOf(activeSport)
                    if (checkedItem == -1) checkedItem = 0 

                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_title_select_sport))
                        .setSingleChoiceItems(sportsArray, checkedItem) { dialog, which ->
                            val selectedSport = sportsArray[which]
                            updateActiveSport(selectedSport)
                            dialog.dismiss()
                        }
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show()
                }
            }
        }
    }

    private fun updateActiveSport(sportName: String) {
        currentUserEmail?.let { email ->
            db.collection("users").document(email)
                .update("grupoActivo", sportName)
                .addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.success_sport_updated, sportName), Toast.LENGTH_SHORT).show()
                    navController.navigate(R.id.nav_home)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, getString(R.string.error_updating_sport, e.message), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showCreateSportDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.dialog_title_create_sport))
        val container = FrameLayout(this)
        val input = EditText(this)
        input.hint = getString(R.string.hint_sport_name)
        container.addView(input)
        val padding = (20 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, 0, padding, 0)
        builder.setView(container)
        builder.setPositiveButton(getString(R.string.action_add)) { _, _ ->
            val sportName = input.text.toString().trim()
            if (sportName.isNotEmpty()) {
                createSportInDatabase(sportName)
            } else {
                Toast.makeText(this, getString(R.string.error_name_empty), Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(getString(R.string.action_cancel), null)
        builder.show()
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
        title = getString(R.string.home_title)
        println("EMAIL ----> $email")
        println("PROVIDER ----> $provider")
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}