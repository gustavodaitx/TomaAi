package br.com.tomai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PessoaConfiancaTest {
    @Test
    fun emailValidoAceitaEnderecoNormalizado() {
        assertTrue(PessoaConfianca.emailValido("  contato@example.com  "))
    }

    @Test
    fun emailValidoRejeitaEnderecoSemDominioOuComEspacos() {
        assertFalse(PessoaConfianca.emailValido("contato@"))
        assertFalse(PessoaConfianca.emailValido("contato exemplo.com"))
    }
}
