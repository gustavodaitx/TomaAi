package br.com.tomai.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsuarioTest {
    @Test
    fun fromFirestoreData_leTelefoneECodigoDeVinculo() {
        val criadoEm = Timestamp.now()
        val usuario = Usuario.fromFirestoreData(
            uid = "uid-paciente",
            data = mapOf(
                "nome" to "Maria Silva",
                "telefone" to "11999999999",
                "email" to "maria@example.com",
                "perfil" to Usuario.PERFIL_PACIENTE,
                "codigoVinculo" to "TMA-7K4P92",
                "ativo" to true,
                "asaasCustomerId" to "asaas-123",
                "responsavelPadraoId" to "responsavel-123"
            ),
            criadoEm = criadoEm
        )

        assertEquals("uid-paciente", usuario.id)
        assertEquals("11999999999", usuario.telefone)
        assertEquals("TMA-7K4P92", usuario.codigoVinculo)
        assertEquals("asaas-123", usuario.asaasCustomerId)
        assertEquals("responsavel-123", usuario.responsavelPadraoId)
        assertEquals(criadoEm, usuario.criadoEm)
    }

    @Test
    fun fromFirestoreData_aceitaDocumentoAntigoSemTelefoneOuCodigo() {
        val usuario = Usuario.fromFirestoreData(
            uid = "uid-antigo",
            data = mapOf("nome" to "Paciente", "email" to "paciente@example.com", "perfil" to "ADMIN")
        )

        assertEquals("", usuario.telefone)
        assertNull(usuario.codigoVinculo)
        assertEquals(Usuario.PERFIL_ADMIN, usuario.perfil)
        assertTrue(usuario.ativo)
    }
}
