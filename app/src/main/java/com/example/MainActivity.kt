package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.example.localization.AppLanguage
import com.example.localization.LocalizationData
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.database.ScanHistoryEntity
import androidx.compose.runtime.collectAsState
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.*

import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.network.ScanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import androidx.compose.ui.draw.clip

class SharedViewModel : ViewModel() {
    private val _scanResult = MutableStateFlow<ScanResponse?>(null)
    val scanResult: StateFlow<ScanResponse?> = _scanResult

    // Path of the JPEG actually captured for this scan, carried Scanner -> Diagnosis
    // so the saved log references the real file instead of a hardcoded mock path.
    private val _imagePath = MutableStateFlow<String?>(null)
    val imagePath: StateFlow<String?> = _imagePath

    fun setScanResult(result: ScanResponse) {
        _scanResult.value = result
    }

    fun setImagePath(path: String) {
        _imagePath.value = path
    }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        KapasApp()
      }
    }
  }
}

@Composable
fun KapasApp(sharedViewModel: SharedViewModel = viewModel()) {
  var currentLanguage by remember { mutableStateOf(AppLanguage.URDU) }
  
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: "home"
  val showBottomBar = currentRoute in listOf("home", "history", "expert")
  
  Scaffold(
    bottomBar = {
        if (showBottomBar) {
            BottomNavBar(navController = navController, currentRoute = currentRoute)
        }
    },
    containerColor = BgMain,
    contentColor = TextPrimary
  ) { paddingValues ->
    NavHost(
      navController = navController, 
      startDestination = "home",
      modifier = Modifier.padding(paddingValues),
      enterTransition = { androidx.compose.animation.fadeIn(animationSpec = tween(300)) },
      exitTransition = { androidx.compose.animation.fadeOut(animationSpec = tween(300)) }
    ) {
      composable("home") { HomeScreen(navController, currentLanguage) { currentLanguage = it } }
      composable("history") { HistoryScreen(navController, currentLanguage) }
      composable("expert") { ExpertScreen(navController) }
      composable("scanner") { ScannerScreen(navController, sharedViewModel) }
      composable("diagnosis") { DiagnosisScreen(navController, sharedViewModel) }
    }
  }
}

@Composable
fun HomeScreen(navController: NavController, currentLanguage: AppLanguage, onLanguageChange: (AppLanguage) -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val appDatabase = (context.applicationContext as CottonAceApplication).database
  val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(16.dp)
  ) {
    // Header Area
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.Top
      ) {
        Column {
          Text(
            text = "Kapas Ki Sehat", 
            style = MaterialTheme.typography.titleLarge, 
            fontWeight = FontWeight.Bold, 
            color = BrandGold, 
            letterSpacing = (-0.5).sp
          )
          Text(
            text = "AGRI-TECH V2.4", 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Normal, 
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, 
            color = TextFaint, 
            letterSpacing = 1.sp
          )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.65f) // Bumped up slightly from 0.55f to give the row more horizontal breathing room
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), // Slightly wider gap between chips
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppLanguage.values().forEach { language ->
                    val isSelected = currentLanguage == language

                    Box(
                        modifier = Modifier
                            .weight(1f) // Keeps them mathematically equal in width
                            .height(36.dp) // Enforces a solid, clear vertical height instead of aspect ratio!
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (isSelected) Color.Transparent else BorderSubtle, RoundedCornerShape(8.dp))
                            .background(if (isSelected) BrandGreen else Surface2)
                            .clickable { onLanguageChange(language) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (language) {
                                AppLanguage.ENGLISH -> "EN"
                                AppLanguage.URDU -> "اردو "
                                AppLanguage.PUNJABI -> "پنجابی "
                                AppLanguage.SARAIKI -> "سرائیکی "
                            },
                            modifier = Modifier.padding(vertical = 1.dp),
                            maxLines = 1, // Strictly prohibits the text from wrapping to a second line and breaking layout heights
                            style = if (language == AppLanguage.ENGLISH) {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                UrduTextStyle.copy(
                                    fontSize = if (language == AppLanguage.URDU) 13.sp else 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            },
                            color = TextPrimary
                        )
                    }
                }
            }
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            color = Surface2,
            shape = CircleShape,
            border = BorderStroke(1.dp, BorderSubtle)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically, 
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Box(modifier = Modifier.size(6.dp).background(SuccessGreen, CircleShape))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "CLOUD SYNC: ACTIVE", 
                fontSize = 9.sp, 
                fontWeight = FontWeight.Bold, 
                color = TextPrimary, 
                letterSpacing = (-0.5).sp
              )
            }
          }
        }
      }
      
      // Welcome Line
      Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
      ) {
        Text(
          text = LocalizationData.greetings[currentLanguage] ?: "", 
          style = if (currentLanguage == AppLanguage.ENGLISH) MaterialTheme.typography.bodyMedium else UrduTextStyle.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
          fontWeight = if (currentLanguage == AppLanguage.ENGLISH) FontWeight.Medium else FontWeight.Bold,
          color = TextPrimary,
          textAlign = if (currentLanguage == AppLanguage.ENGLISH) TextAlign.Left else TextAlign.Right,
          modifier = if (currentLanguage == AppLanguage.ENGLISH) Modifier else Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
        )
      }

      // Main Content Area (Flexible)
      Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
        // District Health Card
        Surface(
          color = Surface1,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, BorderSubtle),
          modifier = Modifier.fillMaxWidth(),
          shadowElevation = 8.dp
        ) {
          Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(), 
              horizontalArrangement = Arrangement.SpaceBetween, 
              verticalAlignment = Alignment.Top
            ) {
              Text(
                text = "DISTRICT HEALTH & RISK", 
                color = BrandGold, 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Black, 
                letterSpacing = 1.sp
              )
              Surface(
                color = Surface3, 
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "MULTAN BELT", 
                  color = TextPrimary, 
                  fontSize = 10.sp, 
                  fontWeight = FontWeight.Bold, 
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
              }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
              modifier = Modifier.fillMaxWidth(), 
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              // Block 1
              Box(
                modifier = Modifier.weight(1f).background(Surface2, RoundedCornerShape(12.dp)).padding(8.dp), 
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(androidx.compose.material.icons.Icons.Default.WbSunny, contentDescription = null, tint = BrandGold, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("37°C", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                  Text("DRY/SUNNY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = (-0.5).sp)
                }
              }
              Spacer(modifier = Modifier.width(8.dp))
              // Block 2
              Box(
                modifier = Modifier.weight(1f).background(Surface2, RoundedCornerShape(12.dp)).padding(8.dp), 
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(androidx.compose.material.icons.Icons.Default.WaterDrop, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("42%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                  Text("HUMIDITY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = (-0.5).sp)
                }
              }
              Spacer(modifier = Modifier.width(8.dp))
              // Block 3
              Box(
                modifier = Modifier.weight(1f).background(Surface2, RoundedCornerShape(12.dp)).padding(8.dp), 
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(androidx.compose.material.icons.Icons.Default.Air, contentDescription = null, tint = BrandGreenSoft, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("14 km/h", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                  Text("WIND SPEED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = (-0.5).sp)
                }
              }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
              color = AlertBg, 
              border = BorderStroke(1.dp, AlertBorder), 
              shape = RoundedCornerShape(8.dp), 
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = LocalizationData.criticalWhiteflyRiskTitle[currentLanguage] ?: "", 
                  color = WarningAmber, 
                  fontSize = if (currentLanguage == AppLanguage.ENGLISH) 10.sp else 14.sp, 
                  fontWeight = FontWeight.Black, 
                  letterSpacing = if (currentLanguage == AppLanguage.ENGLISH) 0.5.sp else 0.sp, 
                  modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth(),
                  textAlign = if (currentLanguage == AppLanguage.ENGLISH) TextAlign.Start else TextAlign.Right,
                  style = if (currentLanguage == AppLanguage.ENGLISH) androidx.compose.ui.text.TextStyle.Default else UrduTextStyle.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl)
                )
                Text(
                  text = LocalizationData.criticalWhiteflyRiskDesc[currentLanguage] ?: "", 
                  color = AlertText, 
                  fontSize = if (currentLanguage == AppLanguage.ENGLISH) 10.sp else 14.sp, 
                  fontWeight = FontWeight.Medium, 
                  lineHeight = if (currentLanguage == AppLanguage.ENGLISH) 14.sp else 24.sp,
                  textAlign = if (currentLanguage == AppLanguage.ENGLISH) TextAlign.Start else TextAlign.Right,
                  style = if (currentLanguage == AppLanguage.ENGLISH) androidx.compose.ui.text.TextStyle.Default else UrduTextStyle.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl)
                )
              }
            }
          }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Hero CTA Button
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth(), 
          contentAlignment = Alignment.Center
        ) {
          Surface(
            onClick = { 
                navController.navigate("scanner") 
            },
            color = BrandGreen,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(4.dp, BorderSubtle),
            modifier = Modifier.fillMaxHeight().fillMaxWidth().testTag("scan_button")
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally, 
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.height(140.dp)
            ) {
              Surface(
                color = Surface1, 
                shape = CircleShape, 
                modifier = Modifier.size(96.dp), 
                shadowElevation = 12.dp
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    androidx.compose.material.icons.Icons.Default.CameraAlt, 
                    contentDescription = null, 
                    tint = BrandGreenSoft, 
                    modifier = Modifier.size(48.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
}

@Composable
fun BottomNavBar(navController: NavController, currentRoute: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(BgMain)
      .drawBehind {
         drawLine(
             color = BorderSubtle, 
             start = androidx.compose.ui.geometry.Offset(0f, 0f), 
             end = androidx.compose.ui.geometry.Offset(size.width, 0f), 
             strokeWidth = 1.dp.toPx()
         )
      }
      .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
      .padding(top = 16.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically
  ) {
     // Home Tab
     Column(
         modifier = Modifier.clickable { 
             if (currentRoute != "home") {
                 navController.navigate("home") {
                     popUpTo("home") { inclusive = true }
                 }
             }
         }.padding(8.dp).let {
             if (currentRoute == "home") it else it.alpha(0.3f)
         },
         horizontalAlignment = Alignment.CenterHorizontally
     ) {
         if (currentRoute == "home") {
             Box(modifier = Modifier.size(6.dp).background(BrandGreenBright, CircleShape))
         } else {
             Icon(
                 androidx.compose.material.icons.Icons.Default.Home, 
                 contentDescription = null, 
                 tint = TextFaint, 
                 modifier = Modifier.size(20.dp)
             )
         }
     }
     
     // History Tab
     Column(
         modifier = Modifier.clickable { 
             if (currentRoute != "history") navController.navigate("history") 
         }.padding(8.dp).let {
             if (currentRoute == "history") it else it.alpha(0.3f)
         },
         horizontalAlignment = Alignment.CenterHorizontally
     ) {
         if (currentRoute == "history") {
             Box(modifier = Modifier.size(6.dp).background(BrandGreenBright, CircleShape))
         } else {
             Icon(
                 androidx.compose.material.icons.Icons.Default.BarChart, 
                 contentDescription = null, 
                 tint = TextFaint, 
                 modifier = Modifier.size(20.dp)
             )
         }
     }
     
     // Expert Tab
     Column(
         modifier = Modifier.clickable { 
             if (currentRoute != "expert") navController.navigate("expert") 
         }.padding(8.dp).let {
             if (currentRoute == "expert") it else it.alpha(0.3f)
         },
         horizontalAlignment = Alignment.CenterHorizontally
     ) {
         if (currentRoute == "expert") {
             Box(modifier = Modifier.size(6.dp).background(BrandGreenBright, CircleShape))
         } else {
             Icon(
                 androidx.compose.material.icons.Icons.Default.Person, 
                 contentDescription = null, 
                 tint = TextFaint, 
                 modifier = Modifier.size(20.dp)
             )
         }
     }
  }
}

@Composable
fun ScannerScreen(navController: NavController, sharedViewModel: SharedViewModel) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var hasCameraPermission by remember {
    mutableStateOf(
      androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean ->
    hasCameraPermission = isGranted
  }

  val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
  ) {}

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }
    locationPermissionLauncher.launch(
        arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
  }

  val fusedLocationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }

  var isScanning by remember { mutableStateOf(false) }

  val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
  val alpha by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = androidx.compose.animation.core.infiniteRepeatable(
          animation = androidx.compose.animation.core.tween(1000),
          repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
      )
  )

  val imageCapture = remember { androidx.camera.core.ImageCapture.Builder().build() }

  if (!hasCameraPermission) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(BgMain)
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
          imageVector = Icons.Default.CropFree,
          contentDescription = null,
          tint = TextPrimary,
          modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
        )
        androidx.compose.material3.Button(
          onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
          colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BrandGreen),
          shape = RoundedCornerShape(12.dp)
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Grant Camera Permission", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
              "کیمرہ تک رسائی کی اجازت دیں",
              style = UrduTextStyle.copy(
                  color = TextPrimary, 
                  fontSize = 16.sp, 
                  fontWeight = FontWeight.Bold,
                  textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
              ),
              textAlign = TextAlign.Right,
              modifier = Modifier.padding(top = 4.dp)
            )
          }
        }
      }
    }
    return
  }

  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    androidx.compose.ui.viewinterop.AndroidView(
      factory = { ctx ->
        val previewView = androidx.camera.view.PreviewView(ctx)
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
        
        cameraProviderFuture.addListener({
          val cameraProvider = cameraProviderFuture.get()
          val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
          }
          val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
          
          try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
          } catch(e: Exception) {
            e.printStackTrace()
          }
        }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
        
        previewView
      },
      modifier = Modifier.fillMaxSize()
    )
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(BrandGreenBright.copy(alpha = alpha), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "READY FOR INFERENCE PIPELINE",
                color = BrandGreenBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "کیمرہ ایکٹیو ہے",
            style = UrduTextStyle.copy(
                color = BrandGreenBright,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
            ),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
    }

    // Simulated viewfinder overlay
    Canvas(modifier = Modifier.fillMaxSize()) {
      val canvasWidth = size.width
      val canvasHeight = size.height
      
      val cutoutSize = canvasWidth * 0.7f
      val cutoutRect = Rect(
        left = (canvasWidth - cutoutSize) / 2f,
        top = (canvasHeight - cutoutSize) / 2f,
        right = (canvasWidth + cutoutSize) / 2f,
        bottom = (canvasHeight + cutoutSize) / 2f
      )

      val backgroundPath = Path().apply {
        addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
      }
      
      val cutoutPath = Path().apply {
        addRoundRect(RoundRect(cutoutRect, CornerRadius(32f, 32f)))
      }
      
      val overlayPath = Path().apply {
        op(backgroundPath, cutoutPath, PathOperation.Difference)
      }

      drawPath(
        path = overlayPath,
        color = BgMain.copy(alpha = 0.8f)
      )
      
      // Draw corner brackets for the scanner
      val cornerLength = 80f
      val strokeWidth = 8f
      
      drawPath(
        path = Path().apply {
          // Top Left
          moveTo(cutoutRect.left, cutoutRect.top + cornerLength)
          lineTo(cutoutRect.left, cutoutRect.top)
          lineTo(cutoutRect.left + cornerLength, cutoutRect.top)
          
          // Top Right
          moveTo(cutoutRect.right - cornerLength, cutoutRect.top)
          lineTo(cutoutRect.right, cutoutRect.top)
          lineTo(cutoutRect.right, cutoutRect.top + cornerLength)
          
          // Bottom Left
          moveTo(cutoutRect.left, cutoutRect.bottom - cornerLength)
          lineTo(cutoutRect.left, cutoutRect.bottom)
          lineTo(cutoutRect.left + cornerLength, cutoutRect.bottom)
          
          // Bottom Right
          moveTo(cutoutRect.right - cornerLength, cutoutRect.bottom)
          lineTo(cutoutRect.right, cutoutRect.bottom)
          lineTo(cutoutRect.right, cutoutRect.bottom - cornerLength)
        },
        color = BrandGreenSoft,
        style = Stroke(width = strokeWidth)
      )
    }

    if (isScanning) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(BgMain.copy(alpha = 0.9f))
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        CircularProgressIndicator(
          color = BrandGreenBright,
          strokeWidth = 6.dp,
          modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
          text = "Kapas AI Diagnostic Matrix",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Calibrating spectral channels...\nاسپیکٹرل کیلیبریشن...",
          style = UrduTextStyle.copy(
              color = TextSecondary,
              fontSize = 16.sp,
              textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
          ),
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
      }
    } else {
      IconButton(
        onClick = { 
            isScanning = true 
            val photoFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
            val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photoFile).build()
            imageCapture.takePicture(
                outputOptions,
                androidx.core.content.ContextCompat.getMainExecutor(context),
                object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: androidx.camera.core.ImageCapture.OutputFileResults) {
                        coroutineScope.launch {
                            try {
                                var lat = 0.0
                                var lon = 0.0
                                
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        @android.annotation.SuppressLint("MissingPermission")
                                        val location = fusedLocationClient.getCurrentLocation(
                                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                                            null
                                        ).await()
                                        
                                        if (location != null) {
                                            lat = location.latitude
                                            lon = location.longitude
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }

                                val response = com.example.network.ApiClient.uploadScan(photoFile, lat, lon)
                                sharedViewModel.setScanResult(response)
                                sharedViewModel.setImagePath(photoFile.absolutePath)
                                navController.navigate("diagnosis") {
                                    popUpTo("home") { inclusive = false }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isScanning = false
                                android.widget.Toast.makeText(
                                    context,
                                    "Scan failed — please try again.\nاسکین ناکام ہوگیا، دوبارہ کوشش کریں۔",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    override fun onError(exc: androidx.camera.core.ImageCaptureException) {
                        exc.printStackTrace()
                        isScanning = false
                        android.widget.Toast.makeText(
                            context,
                            "Couldn't capture image — please try again.\nتصویر نہیں لی جا سکی، دوبارہ کوشش کریں۔",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 64.dp)
          .size(80.dp)
          .background(BrandGreenBright, CircleShape)
          .testTag("capture_button")
      ) {
        Icon(
          imageVector = Icons.Default.CameraAlt,
          contentDescription = "Capture",
          tint = BgMain,
          modifier = Modifier.size(40.dp)
        )
      }
    }
  }
}

@Composable
fun DiagnosisScreen(navController: NavController, sharedViewModel: SharedViewModel) {
  val scanResult by sharedViewModel.scanResult.collectAsState()
  val capturedImagePath by sharedViewModel.imagePath.collectAsState()
  val pestType = scanResult?.pest_type ?: "Unknown Analysis"
  val confidence = scanResult?.confidence ?: 0f
  val recommendation = scanResult?.recommendation_ur ?: "No recommendation available."

  val context = androidx.compose.ui.platform.LocalContext.current
  val appDatabase = (context.applicationContext as CottonAceApplication).database
  val coroutineScope = rememberCoroutineScope()
  var isSubmitting by remember { mutableStateOf(false) }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = BgMain
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(24.dp)
    ) {
      Text(
        text = "Agro AI Log Engine",
        style = MaterialTheme.typography.labelMedium,
        color = BrandGreenSoft,
        letterSpacing = 1.5.sp
      )
      Text(
        text = "Leaves Scan Diagnostics",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )
      
      Spacer(modifier = Modifier.height(32.dp))
      
      // Identified Attack Area
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AlertBg,
        border = BorderStroke(2.dp, AlertBorder),
        shape = RoundedCornerShape(16.dp)
      ) {
        Row(
          modifier = Modifier.padding(20.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(12.dp)
              .background(DangerRed, CircleShape)
          )
          Spacer(modifier = Modifier.width(16.dp))
          Column {
            Text(
              text = pestType,
              style = MaterialTheme.typography.titleLarge,
              color = DangerRed,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = pestType,
              style = UrduTextStyle.copy(
                  color = AlertText,
                  fontSize = 18.sp,
                  fontWeight = FontWeight.Medium,
                  textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
              ),
              textAlign = TextAlign.Right,
              modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
          }
        }
      }
      
      Spacer(modifier = Modifier.height(24.dp))
      
      // Severity Gauge
      Column(
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "${(confidence * 100).toInt()}% Confidence", color = DangerRed, fontWeight = FontWeight.Bold)
          Text(
            text = "تشخیص کا اعتماد", 
            style = UrduTextStyle.copy(
                color = DangerRed,
                fontSize = 14.sp,
                textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
            ),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
          progress = { confidence },
          modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
          color = DangerRed,
          trackColor = Surface3,
          strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
      }
      
      Spacer(modifier = Modifier.height(32.dp))
      
      // Recommendations Card
      Surface(
        modifier = Modifier.fillMaxWidth().weight(1f),
        color = Surface1,
        shape = RoundedCornerShape(16.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
        ) {
          Text(
            text = "ACTION PROTOCOL",
            style = MaterialTheme.typography.labelLarge,
            color = BrandGold,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
          )
          
          Spacer(modifier = Modifier.height(16.dp))
          
          BulletPoint(recommendation)
        }
      }
      
      Spacer(modifier = Modifier.height(24.dp))
      
      // Save Button
      Button(
        onClick = {
            if (isSubmitting) return@Button
            isSubmitting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val newScan = ScanHistoryEntity(
                        timestamp = System.currentTimeMillis(),
                        imagePath = capturedImagePath ?: "",
                        whiteflyCount = if (pestType.contains("Whitefly", ignoreCase = true)) 15 else (5..45).random(),
                        riskLevel = if (confidence > 0.8f) "CRITICAL" else "MEDIUM",
                        district = "Multan Belt"
                    )
                    appDatabase.scanHistoryDao().insertScan(newScan)

                    android.util.Log.d("CottonAceSync", "Save Clicked! Force-enqueuing WorkManager request manually right now...")
                    
                    val workManager = WorkManager.getInstance(context)
                    val syncRequest = OneTimeWorkRequestBuilder<com.example.network.DataSyncWorker>().build()
                    
                    workManager.enqueueUniqueWork(
                        "CottonAceDataSync",
                        ExistingWorkPolicy.REPLACE,
                        syncRequest
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        navController.popBackStack()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        isSubmitting = false
                    }
                }
            }
        },
        enabled = !isSubmitting,
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .testTag("save_log_button")
          .alpha(if (isSubmitting) 0.5f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Surface2,
          contentColor = TextPrimary
        )
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = "Save",
          tint = SuccessGreen,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Save to Log & Return",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
fun ExpertScreen(navController: NavController) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(16.dp)
  ) {
    Text(
        text = "EXPERT CONNECT", 
        style = MaterialTheme.typography.titleLarge, 
        fontWeight = FontWeight.Bold, 
        color = BrandGold, 
        modifier = Modifier.padding(bottom = 24.dp)
      )
      
      Surface(
        color = Surface1,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().weight(1f)
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = BrandGreenSoft,
            modifier = Modifier.size(64.dp)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Connect with an Agronomist",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "زرعی ماہر سے رابطہ کریں",
            style = UrduTextStyle.copy(
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
            ),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(top = 8.dp)
          )
        }
      }
    }
}

@Composable
fun HistoryScreen(navController: NavController, currentLanguage: AppLanguage) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val appDatabase = (context.applicationContext as CottonAceApplication).database
  val scanHistoryList by appDatabase.scanHistoryDao().getAllScans().collectAsState(initial = emptyList())

  Column(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(16.dp)
  ) {
    val titleText = when (currentLanguage) {
        AppLanguage.ENGLISH -> "HEALTH LOGS"
        AppLanguage.URDU -> "صحت کے لاگز"
        AppLanguage.PUNJABI, AppLanguage.SARAIKI -> "صحت دے لاگز"
    }

    Text(
        text = titleText, 
        style = if (currentLanguage == AppLanguage.ENGLISH) MaterialTheme.typography.titleLarge else UrduTextStyle.copy(fontSize = 26.sp), 
        fontWeight = FontWeight.Bold, 
        color = BrandGold, 
        textAlign = if (currentLanguage == AppLanguage.ENGLISH) TextAlign.Start else TextAlign.End,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
      )
      
      LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f),
          verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
          items(scanHistoryList) { scan ->
              Surface(
                  color = Surface1,
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth()
              ) {
                  // Canonical risk enum (CONTRACTS.md §4): LOW / MEDIUM / HIGH / CRITICAL.
                  val riskColor = when(scan.riskLevel) {
                      "CRITICAL" -> DangerRed
                      "HIGH" -> DangerRed
                      "MEDIUM" -> WarningAmber
                      "LOW" -> SuccessGreen
                      else -> TextSecondary // unknown value: neutral, never imply "healthy"
                  }

                  val badge = @Composable {
                      Surface(
                          color = riskColor.copy(alpha = 0.1f),
                          shape = RoundedCornerShape(8.dp),
                          border = BorderStroke(1.dp, riskColor)
                      ) {
                          Text(
                              text = scan.riskLevel,
                              color = riskColor,
                              fontSize = 12.sp,
                              fontWeight = FontWeight.Bold,
                              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                          )
                      }
                  }

                  val topTextContent = @Composable {
                      Column(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalAlignment = if (currentLanguage == AppLanguage.ENGLISH) Alignment.Start else Alignment.End
                      ) {
                          val sdf = java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault())
                          Text(
                              text = sdf.format(java.util.Date(scan.timestamp)),
                              color = TextSecondary,
                              fontSize = 12.sp
                          )
                          Spacer(modifier = Modifier.height(4.dp))
                          val localizedDistrict = if (scan.district == "Multan Belt") {
                              when (currentLanguage) {
                                  AppLanguage.ENGLISH -> "Multan Belt"
                                  AppLanguage.URDU, AppLanguage.PUNJABI, AppLanguage.SARAIKI -> "ملتان بیلٹ"
                              }
                          } else scan.district

                          Text(
                              text = localizedDistrict,
                              color = TextPrimary,
                              fontSize = 16.sp,
                              fontWeight = FontWeight.Bold,
                              textAlign = if (currentLanguage == AppLanguage.ENGLISH) TextAlign.Start else TextAlign.Right,
                              modifier = Modifier.fillMaxWidth(),
                              style = if (currentLanguage == AppLanguage.ENGLISH) androidx.compose.ui.text.TextStyle.Default else UrduTextStyle.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl)
                          )
                      }
                  }

                  val whiteflyText = @Composable {
                      val whiteflyLabel = when (currentLanguage) {
                          AppLanguage.ENGLISH -> "Whitefly Count: "
                          AppLanguage.URDU -> "چٹی مکھی کی تعداد: "
                          AppLanguage.PUNJABI, AppLanguage.SARAIKI -> "چٹی مکھی دی تعداد: "
                      }
                      Text(
                          text = "$whiteflyLabel${scan.whiteflyCount}",
                          color = TextSecondary,
                          fontSize = if (currentLanguage == AppLanguage.ENGLISH) 14.sp else 16.sp,
                          textAlign = if (currentLanguage == AppLanguage.ENGLISH) TextAlign.Start else TextAlign.Right,
                          modifier = Modifier.fillMaxWidth(),
                          style = if (currentLanguage == AppLanguage.ENGLISH) androidx.compose.ui.text.TextStyle.Default else UrduTextStyle.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl)
                      )
                  }

                  if (currentLanguage == AppLanguage.ENGLISH) {
                      Row(
                          modifier = Modifier.padding(16.dp),
                          verticalAlignment = Alignment.CenterVertically
                      ) {
                          Column(modifier = Modifier.weight(1f)) {
                              topTextContent()
                              Spacer(modifier = Modifier.height(4.dp))
                              whiteflyText()
                          }
                          badge()
                      }
                  } else {
                      Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                          topTextContent()
                          Spacer(modifier = Modifier.height(12.dp))
                          Row(
                              modifier = Modifier.fillMaxWidth(),
                              horizontalArrangement = Arrangement.SpaceBetween,
                              verticalAlignment = Alignment.CenterVertically
                          ) {
                              badge()
                              Box(modifier = Modifier.weight(1f)) {
                                  whiteflyText()
                              }
                          }
                      }
                  }
              }
          }
      }
    }
}

@Composable
fun BulletPoint(text: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = "•",
      style = MaterialTheme.typography.bodyLarge,
      color = TextPrimary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(end = 12.dp, top = 2.dp)
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = TextSecondary,
      lineHeight = 22.sp
    )
  }
}
