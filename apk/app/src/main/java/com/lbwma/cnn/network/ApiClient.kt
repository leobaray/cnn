package com.lbwma.cnn.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Foto(val nome: String, val tamanhoKb: Double)

object ApiClient {
    val baseUrl: String = "https://server.lbwma.com"
    private var credentials: String = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun configure(user: String, pass: String) {
        credentials = Credentials.basic(user, pass)
    }

    fun logout() {
        credentials = ""
    }

    private fun authRequest(url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", credentials)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    sealed class LoginResult { data object Ok : LoginResult(); data object Unauthorized : LoginResult(); data object NetworkError : LoginResult() }

    suspend fun testConnection(): LoginResult = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$baseUrl/conversores").get().build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> LoginResult.Ok
                    response.code == 401 || response.code == 403 -> LoginResult.Unauthorized
                    else -> LoginResult.NetworkError
                }
            }
        } catch (_: Exception) {
            LoginResult.NetworkError
        }
    }

    suspend fun getConversores(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$baseUrl/conversores").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Erro ${response.code}"))
                val body = response.body?.string() ?: "[]"
                val json = try {
                    JSONArray(body)
                } catch (_: Exception) {
                    JSONObject(body).getJSONArray("conversores")
                }
                val list = (0 until json.length()).map { i ->
                    val item = json.get(i)
                    if (item is JSONObject) item.getString("nome") else item.toString()
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createConversor(nome: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$baseUrl/conversores?nome=${encode(nome)}")
                .post("".toRequestBody(null))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Erro ${response.code}"))
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFotos(nome: String): Result<List<Foto>> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$baseUrl/conversores/${encode(nome)}/fotos").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Erro ${response.code}"))
                val body = response.body?.string() ?: "[]"
                val fotosArray = try {
                    JSONArray(body)
                } catch (_: Exception) {
                    JSONObject(body).getJSONArray("fotos")
                }
                val list = (0 until fotosArray.length()).map { i ->
                    val item = fotosArray.get(i)
                    if (item is JSONObject) {
                        Foto(
                            nome = item.getString("nome"),
                            tamanhoKb = item.optDouble("tamanho_kb", 0.0)
                        )
                    } else {
                        Foto(nome = item.toString(), tamanhoKb = 0.0)
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFoto(nome: String, fileName: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("fotos", fileName, bytes.toRequestBody("image/*".toMediaType()))
                .build()
            val request = authRequest("$baseUrl/conversores/${encode(nome)}/fotos")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Erro ${response.code}"))
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameConversor(nomeAtual: String, novoNome: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$baseUrl/conversores/${encode(nomeAtual)}?novo_nome=${encode(novoNome)}")
                .patch("".toRequestBody(null))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        404 -> "Conversor não encontrado"
                        409 -> "Já existe um conversor com esse nome"
                        400 -> "Nome inválido"
                        else -> "Erro ${response.code}"
                    }
                    return@withContext Result.failure(Exception(msg))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteConversor(nome: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$baseUrl/conversores/${encode(nome)}")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        404 -> "Conversor não encontrado"
                        else -> "Erro ${response.code}"
                    }
                    return@withContext Result.failure(Exception(msg))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFoto(nome: String, arquivo: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$baseUrl/conversores/${encode(nome)}/fotos/${encode(arquivo)}")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Erro ${response.code}"))
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFotoUrl(nome: String, arquivo: String): String =
        "$baseUrl/conversores/${encode(nome)}/fotos/${encode(arquivo)}/download"

    fun getThumbUrl(nome: String, arquivo: String): String =
        "$baseUrl/conversores/${encode(nome)}/fotos/${encode(arquivo)}/thumb"

    suspend fun checkUpdate(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/app/version").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Erro ${response.code}"))
                val json = JSONObject(response.body!!.string())
                Result.success(json.getInt("versionCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class InferResult(
        val classe: String,
        val confianca: Float,
        val top5: List<Pair<String, Float>>
    )

    suspend fun infer(imageBytes: ByteArray, fileName: String, tta: Boolean = false): Result<InferResult> = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("foto", fileName, imageBytes.toRequestBody("image/*".toMediaType()))
                .build()
            val url = if (tta) "$baseUrl/infer?tta=true" else "$baseUrl/infer"
            val request = authRequest(url).post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Erro ${response.code}"))
                val json = JSONObject(response.body!!.string())
                val top5Array = json.getJSONArray("top5")
                val top5 = (0 until top5Array.length()).map { i ->
                    val item = top5Array.getJSONObject(i)
                    item.getString("class") to item.getDouble("confidence").toFloat()
                }
                Result.success(InferResult(
                    classe = json.getString("class"),
                    confianca = json.getDouble("confidence").toFloat(),
                    top5 = top5
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class InferStatus(val ready: Boolean, val numClasses: Int, val classNames: List<String>)

    suspend fun inferStatus(): Result<InferStatus> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest("$baseUrl/infer/status").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Erro ${response.code}"))
                val json = JSONObject(response.body!!.string())
                val names = json.optJSONArray("class_names")
                val classNames = if (names != null) (0 until names.length()).map { names.getString(it) } else emptyList()
                Result.success(InferStatus(
                    ready = json.getBoolean("ready"),
                    numClasses = json.optInt("num_classes", 0),
                    classNames = classNames
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getApkDownloadUrl(): String = "$baseUrl/app/download"

    fun getAuthHeader(): String = credentials

    fun getAuthInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Authorization", credentials)
            .build()
        chain.proceed(request)
    }
}
