package br.com.tomai.data.repository

import br.com.tomai.model.Medicamento
import kotlinx.coroutines.flow.Flow

interface MedicamentoRepository {

    data class ResultadoExclusao(
        val usuarioId: String,
        val doseIdsRemovidas: List<String>,
        val horariosRemovidos: Int
    )

    fun observarMedicamentos(usuarioId: String): Flow<List<Medicamento>>

    suspend fun buscarPorId(medicamentoId: String): Result<Medicamento?>

    suspend fun salvar(medicamento: Medicamento): Result<Medicamento>

    suspend fun excluir(medicamentoId: String): Result<ResultadoExclusao>
}
