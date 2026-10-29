package br.com.tomai.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseEmulatorTest {

    @Test
    fun cadastrar_criaPerfilCorrespondenteNoFirestoreEmulator() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:abcdef1234567890")
                .setApiKey("AIzaSyDemoTomaAiEmulatorKey0000000000")
                .setProjectId(PROJECT_ID)
                .build(),
            "integration-test-${UUID.randomUUID()}"
        )

        try {
            val auth = FirebaseAuth.getInstance(app)
            val firestore = FirebaseFirestore.getInstance(app)
            auth.useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT)
            firestore.useEmulator(EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)

            val repository = AuthRepositoryImpl(
                auth = auth,
                firestore = firestore,
                functions = FirebaseFunctions.getInstance(app)
            )
            val email = "integration-${UUID.randomUUID()}@example.test"
            val result = repository.cadastrar(
                nome = "Paciente de integração",
                telefone = "11999999999",
                email = email,
                senha = "TesteLocal!123"
            )
            val usuario = result.getOrElse {
                throw AssertionError("Cadastro no Firebase Emulator falhou: ${it.message}", it)
            }

            assertFalse(usuario.id.isBlank())
            assertEquals(email, usuario.email)

            val perfil = firestore.collection(AuthRepositoryImpl.COLECAO_USUARIOS)
                .document(usuario.id)
                .get()
                .await()

            assertTrue(perfil.exists())
            assertEquals(usuario.id, perfil.getString("id"))
            assertEquals("Paciente de integração", perfil.getString("nome"))
            assertEquals("PACIENTE", perfil.getString("perfil"))
        } finally {
            app.delete()
        }
    }

    private companion object {
        const val PROJECT_ID = "demo-tomai"
        const val EMULATOR_HOST = "10.0.2.2"
        const val AUTH_EMULATOR_PORT = 9099
        const val FIRESTORE_EMULATOR_PORT = 8080
    }
}
