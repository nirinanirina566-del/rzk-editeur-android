
package com.rzk.editeur

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RZKEditorApp()
        }
    }
}

@Composable
fun RZKEditorApp() {
    val context = LocalContext.current

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Prêt") }
    var resultUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri = uri
        resultUrl = ""
        if (uri != null) status = "Photo sélectionnée"
    }

    val client = remember { OkHttpClient() }
    val backend = "https://rzk-editeur-backend.onrender.com"

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "RZK EDITEUR",
                style = MaterialTheme.typography.headlineLarge
            )

            Text("MASTER QUALITY • 4K • AUTO COLOR")

            Button(
                onClick = { picker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choisir une photo")
            }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("APP ACCESS TOKEN") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Réglages :")
            Text("• Amélioration photo professionnelle")
            Text("• Couleur automatique")
            Text("• Préservation du visage original")
            Text("• Détails naturels")

            Button(
                onClick = {
                    val uri = imageUri
                    val accessToken = token

                    if (uri == null) {
                        status = "Choisissez d'abord une photo"
                    } else if (accessToken.isBlank()) {
                        status = "Entrez votre APP ACCESS TOKEN"
                    } else {
                        loading = true
                        status = "Envoi de la photo..."

                        Thread {
                            try {
                                val bytes = context.contentResolver
                                    .openInputStream(uri)!!
                                    .use { it.readBytes() }

                                val body = MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart(
                                        "image",
                                        "photo.jpg",
                                        bytes.toRequestBody(
                                            "image/jpeg".toMediaType()
                                        )
                                    )
                                    .build()

                                val request = Request.Builder()
                                    .url("$backend/enhance")
                                    .addHeader(
                                        "Authorization",
                                        "Bearer $accessToken"
                                    )
                                    .post(body)
                                    .build()

                                val response = client.newCall(request).execute()
                                val responseText =
                                    response.body?.string() ?: ""

                                if (!response.isSuccessful) {
                                    throw IOException(
                                        "Erreur serveur: ${response.code} $responseText"
                                    )
                                }

                                val json = JSONObject(responseText)
                                val processId = json.optString(
                                    "process_id",
                                    json.optString("id")
                                )

                                if (processId.isBlank()) {
                                    throw IOException(
                                        "Identifiant de traitement introuvable: $responseText"
                                    )
                                }

                                mainHandler.post {
                                    status = "Traitement en cours..."
                                }

                                var finished = false

                                for (i in 1..60) {
                                    Thread.sleep(3000)

                                    val statusRequest = Request.Builder()
                                        .url("$backend/status/$processId")
                                        .addHeader(
                                            "Authorization",
                                            "Bearer $accessToken"
                                        )
                                        .get()
                                        .build()

                                    val statusResponse =
                                        client.newCall(statusRequest).execute()

                                    val statusText =
                                        statusResponse.body?.string() ?: ""

                                    if (!statusResponse.isSuccessful) {
                                        throw IOException(
                                            "Erreur status: ${statusResponse.code}"
                                        )
                                    }

                                    val statusJson = JSONObject(statusText)
                                    val currentStatus = statusJson
                                        .optString("status")
                                        .lowercase()

                                    mainHandler.post {
                                        status = "Traitement : $currentStatus"
                                    }

                                    if (currentStatus in listOf(
                                            "completed",
                                            "complete",
                                            "success",
                                            "succeeded"
                                        )
                                    ) {
                                        val resultRequest = Request.Builder()
                                            .url("$backend/result/$processId")
                                            .addHeader(
                                                "Authorization",
                                                "Bearer $accessToken"
                                            )
                                            .get()
                                            .build()

                                        val resultResponse =
                                            client.newCall(resultRequest).execute()

                                        val resultText =
                                            resultResponse.body?.string() ?: ""

                                        if (!resultResponse.isSuccessful) {
                                            throw IOException(
                                                "Erreur résultat: ${resultResponse.code}"
                                            )
                                        }

                                        val resultJson = JSONObject(resultText)

                                        val url = resultJson.optString(
                                            "url",
                                            resultJson.optString(
                                                "download_url",
                                                resultJson.optString("result_url")
                                            )
                                        )

                                        mainHandler.post {
                                            resultUrl = url
                                            status = if (url.isNotBlank()) {
                                                "Sary vita! Afaka sokafana ny vokatra."
                                            } else {
                                                "Vita ny traitement: $resultText"
                                            }
                                            loading = false
                                        }

                                        finished = true
                                        break
                                    }

                                    if (currentStatus in listOf(
                                            "failed",
                                            "error",
                                            "canceled"
                                        )
                                    ) {
                                        throw IOException(
                                            "Tsy nahomby ny traitement: $statusText"
                                        )
                                    }
                                }

                                if (!finished) {
                                    throw IOException(
                                        "Nihoatra ny fotoana fiandrasana."
                                    )
                                }

                            } catch (e: Exception) {
                                mainHandler.post {
                                    status = "Erreur : ${e.message}"
                                    loading = false
                                }
                            }
                        }.start()
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (loading) "Traitement en cours..."
                    else "AMÉLIORER LA PHOTO"
                )
            }

            Text("État : $status")

            if (resultUrl.isNotBlank()) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(resultUrl)
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ouvrir la photo améliorée")
                }
            }
        }
    }
}

