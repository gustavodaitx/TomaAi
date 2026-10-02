import * as admin from "firebase-admin";

export class ErroCadastroPessoaDeConfianca extends Error {
  constructor(readonly codigo: "unauthenticated" | "invalid-argument" | "not-found" | "already-exists" | "failed-precondition", message: string) {
    super(message);
    this.name = "ErroCadastroPessoaDeConfianca";
  }
}

export async function criarPerfilPessoaDeConfianca(
  uid: string,
  email: string,
  dados: unknown,
  firestore: admin.firestore.Firestore = admin.firestore()
) {
  const registro = typeof dados === "object" && dados !== null ? dados as { nome?: unknown; telefone?: unknown; codigoPaciente?: unknown } : {};
  const nome = typeof registro.nome === "string" ? registro.nome.trim() : "";
  const telefone = typeof registro.telefone === "string" ? registro.telefone.trim() : "";
  const codigo = typeof registro.codigoPaciente === "string" ? registro.codigoPaciente.trim().toUpperCase() : "";
  const emailNormalizado = email.trim();
  if (!uid.trim() || !emailNormalizado) throw new ErroCadastroPessoaDeConfianca("unauthenticated", "Faça login para continuar.");
  if (nome.length < 2 || telefone.replace(/\D/g, "").length < 10 || !/^TMA-[A-Z0-9]{6}$/.test(codigo)) {
    throw new ErroCadastroPessoaDeConfianca("invalid-argument", "Informe nome, telefone e código do paciente válidos.");
  }

  const usuarioRef = firestore.collection("usuarios").doc(uid);
  const codigoRef = firestore.collection("codigos_vinculo_pacientes").doc(codigo);
  return firestore.runTransaction(async (transaction) => {
    const [usuarioSnapshot, codigoSnapshot] = await Promise.all([transaction.get(usuarioRef), transaction.get(codigoRef)]);
    const pacienteUid = codigoSnapshot.data()?.pacienteUid;
    if (!codigoSnapshot.exists || typeof pacienteUid !== "string" || !pacienteUid) {
      throw new ErroCadastroPessoaDeConfianca("not-found", "Código do paciente inválido.");
    }
    if (pacienteUid === uid) throw new ErroCadastroPessoaDeConfianca("failed-precondition", "Use uma conta diferente da conta do paciente.");
    if (usuarioSnapshot.exists) {
      const existente = usuarioSnapshot.data();
      if (existente?.perfil === "PESSOA_DE_CONFIANCA" && existente.pacienteUid === pacienteUid) {
        const aceitouReceberAvisos = typeof existente.aceitouReceberAvisos === "boolean" ? existente.aceitouReceberAvisos : true;
        transaction.set(usuarioRef, { codigoPacienteDigitado: codigo, aceitouReceberAvisos }, { merge: true });
        return {
          nome: existente.nome,
          telefone: existente.telefone,
          email: existente.email,
          perfil: "PESSOA_DE_CONFIANCA",
          codigoPacienteDigitado: codigo,
          pacienteUid,
          aceitouReceberAvisos,
        };
      }
      const perfilProvisorio = existente?.perfil === "PACIENTE" &&
        !existente.codigoVinculo && !existente.codigoPaciente;
      if (!perfilProvisorio) {
        throw new ErroCadastroPessoaDeConfianca("already-exists", "O perfil deste usuário já foi criado.");
      }
    }
    const perfil = {
      id: uid,
      nome,
      telefone,
      email: emailNormalizado,
      perfil: "PESSOA_DE_CONFIANCA",
      codigoPacienteDigitado: codigo,
      pacienteUid,
      aceitouReceberAvisos: true,
      ativo: true,
      criadoEm: admin.firestore.FieldValue.serverTimestamp(),
    };
    transaction.set(usuarioRef, perfil, { merge: true });
    return { nome, telefone, email: emailNormalizado, perfil: "PESSOA_DE_CONFIANCA", codigoPacienteDigitado: codigo, pacienteUid, aceitouReceberAvisos: true };
  });
}
