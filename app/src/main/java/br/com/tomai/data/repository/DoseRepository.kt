package br.com.tomai.data.repository

import br.com.tomai.model.Dose
import br.com.tomai.model.Medicamento
import kotlinx.coroutines.flow.Flow

interface DoseRepository {

    fun observarDosesDoDia(usuarioId: String, dataAgenda: String): Flow<List<Dose>>

    suspend fun garantirAgendaDiaria(
        usuarioId: String,
        medicamentos: List<Medicamento>,
        dataAgenda: String
    ): Result<Unit>

    suspend fun atualizarStatus(
        doseId: String,
        novoStatus: String,
        medicamentoId: String?
    ): Result<Dose>
}
