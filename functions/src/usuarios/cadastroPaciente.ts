import { randomInt } from "crypto";
import * as admin from "firebase-admin";

export type CodigoErroCadastroPaciente =
  | "unauthenticated"
  | "invalid-argument"
  | "already-exists"
  | "failed-precondition"
  | "resource-exhausted";

export class ErroCadastroPaciente extends Error {
  constructor(readonly codigo: CodigoErroCadastroPaciente, message: string) {
    super(message);
    this.name = "ErroCadastroPaciente";
  }
}

export interface IdentidadeAutenticada {
  uid: string;
  email: string;
}

export function obterIdentidadeAutenticada(
  auth: { uid?: unknown; token?: { email?: unknown } } | null | undefined
): IdentidadeAutenticada {
  const uid = typeof auth?.uid === "string" ? auth.uid.trim() : "";
  const email = typeof auth?.token?.email === "string" ? auth.token.email.trim() : "";
  if (!uid || !email) {
    throw new ErroCadastroPaciente("unauthenticated", "Faça login para continuar.");
  }
  return { uid, email };
}

export interface PerfilPacienteCriado {
  codigoVinculo: string;
  codigoPaciente: string;
  nome: string;
  telefone: string;
  email: string;
  perfil: "PACIENTE";
}

const ALFABETO_CODIGO_VINCULO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function gerarCodigoVinculo(): string {
  const sufixo = Array.from({ length: 6 }, () =>
    ALFABETO_CODIGO_VINCULO[randomInt(ALFABETO_CODIGO_VINCULO.length)]
  ).join("");
  return `TMA-${sufixo}`;
}

export async function garantirCodigoPaciente(
  uid: string,
  firestore: admin.firestore.Firestore = admin.firestore(),
  gerarCodigo: () => string = gerarCodigoVinculo
): Promise<string> {
  if (!uid.trim()) throw new ErroCadastroPaciente("unauthenticated", "Faça login para continuar.");
  const usuarioRef = firestore.collection("usuarios").doc(uid);

  for (let tentativa = 0; tentativa < 10; tentativa++) {
    const codigoCandidato = gerarCodigo();
    const codigoCandidatoRef = firestore.collection("codigos_vinculo_pacientes").doc(codigoCandidato);
    const codigoExistenteRef = firestore.collection("codigos_vinculo_pacientes");
    const resultado = await firestore.runTransaction(async (transaction): Promise<string | null> => {
      const usuarioSnapshot = await transaction.get(usuarioRef);
      if (!usuarioSnapshot.exists || usuarioSnapshot.data()?.perfil !== "PACIENTE") {
        throw new ErroCadastroPaciente("failed-precondition", "Perfil de paciente não encontrado.");
      }

      const codigoAtual = usuarioSnapshot.data()?.codigoPaciente || usuarioSnapshot.data()?.codigoVinculo;
      const codigoAtualRef = typeof codigoAtual === "string" && codigoAtual.trim()
        ? codigoExistenteRef.doc(codigoAtual)
        : null;
      const leituras = [transaction.get(codigoCandidatoRef)];
      if (codigoAtualRef && codigoAtualRef.path !== codigoCandidatoRef.path) leituras.push(transaction.get(codigoAtualRef));
      const snapshots = await Promise.all(leituras);
      const candidatoSnapshot = snapshots[0];
      const codigoAtualSnapshot = codigoAtualRef
        ? codigoAtualRef.path === codigoCandidatoRef.path ? candidatoSnapshot : snapshots[1]
        : undefined;

      if (typeof codigoAtual === "string" && codigoAtual.trim() &&
        (!codigoAtualSnapshot?.exists || codigoAtualSnapshot.data()?.pacienteUid === uid)) {
        if (!codigoAtualSnapshot?.exists) {
          transaction.create(codigoAtualRef!, {
            pacienteUid: uid,
            criadoEm: admin.firestore.FieldValue.serverTimestamp(),
          });
        }
        transaction.set(usuarioRef, { codigoPaciente: codigoAtual, codigoVinculo: codigoAtual }, { merge: true });
        return codigoAtual;
      }

      if (candidatoSnapshot.exists) return null;
      transaction.create(codigoCandidatoRef, {
        pacienteUid: uid,
        criadoEm: admin.firestore.FieldValue.serverTimestamp(),
      });
      transaction.set(usuarioRef, {
        codigoPaciente: codigoCandidato,
        codigoVinculo: codigoCandidato,
      }, { merge: true });
      return codigoCandidato;
    });
    if (resultado) return resultado;
  }

  throw new ErroCadastroPaciente("resource-exhausted", "Não foi possível gerar o código. Tente novamente.");
}

export async function criarPerfilPaciente(
    uid: string,
    email: string,
    dados: unknown,
  firestore: admin.firestore.Firestore = admin.firestore(),
  gerarCodigo: () => string = gerarCodigoVinculo
): Promise<PerfilPacienteCriado> {
  const dadosRegistro = typeof dados === "object" && dados !== null
    ? dados as { nome?: unknown; telefone?: unknown }
    : {};
  const nome = typeof dadosRegistro.nome === "string" ? dadosRegistro.nome.trim() : "";
  const telefone = typeof dadosRegistro.telefone === "string" ? dadosRegistro.telefone.trim() : "";
  const emailNormalizado = email.trim();

  if (!uid.trim()) {
    throw new ErroCadastroPaciente("unauthenticated", "Faça login para continuar.");
  }
  if (nome.length < 2 || telefone.replace(/\D/g, "").length < 10 || !emailNormalizado) {
    throw new ErroCadastroPaciente("invalid-argument", "Informe nome, telefone e e-mail válidos.");
  }

  const usuarioRef = firestore.collection("usuarios").doc(uid);
  for (let tentativa = 0; tentativa < 10; tentativa++) {
    const resultado = await firestore.runTransaction(async (transaction): Promise<PerfilPacienteCriado | null> => {
      const usuarioSnapshot = await transaction.get(usuarioRef);
      const usuarioExistente = usuarioSnapshot.data();
      if (usuarioSnapshot.exists && usuarioExistente?.perfil === "PACIENTE" &&
        typeof (usuarioExistente.codigoPaciente || usuarioExistente.codigoVinculo) === "string" &&
        String(usuarioExistente.codigoPaciente || usuarioExistente.codigoVinculo).trim()) {
        const codigoExistente = String(usuarioExistente.codigoPaciente || usuarioExistente.codigoVinculo);
        const codigoExistenteRef = firestore.collection("codigos_vinculo_pacientes").doc(codigoExistente);
        const codigoExistenteSnapshot = await transaction.get(codigoExistenteRef);
        if (codigoExistenteSnapshot.exists && codigoExistenteSnapshot.data()?.pacienteUid !== uid) {
          throw new ErroCadastroPaciente("already-exists", "O código de vínculo não está disponível.");
        }
        if (!codigoExistenteSnapshot.exists) {
          transaction.create(codigoExistenteRef, {
            pacienteUid: uid,
            criadoEm: admin.firestore.FieldValue.serverTimestamp(),
          });
        }
        transaction.set(usuarioRef, {
          nome,
          telefone,
          email: emailNormalizado,
          perfil: "PACIENTE",
          ativo: true,
          codigoVinculo: codigoExistente,
          codigoPaciente: codigoExistente,
        }, { merge: true });
        return {
          codigoVinculo: codigoExistente,
          codigoPaciente: codigoExistente,
          nome,
          telefone,
          email: emailNormalizado,
          perfil: "PACIENTE",
        };
      }
      if (usuarioSnapshot.exists && usuarioExistente?.perfil !== "PACIENTE") {
        throw new ErroCadastroPaciente("already-exists", "O perfil deste usuário já foi criado.");
      }

      const codigoVinculo = gerarCodigo();
      const codigoRef = firestore.collection("codigos_vinculo_pacientes").doc(codigoVinculo);
      const codigoSnapshot = await transaction.get(codigoRef);
      if (codigoSnapshot.exists) return null;

      transaction.create(codigoRef, {
        pacienteUid: uid,
        criadoEm: admin.firestore.FieldValue.serverTimestamp(),
      });
      const perfil = {
        id: uid,
        nome,
        telefone,
        email: emailNormalizado,
        perfil: "PACIENTE",
        ativo: true,
        criadoEm: admin.firestore.FieldValue.serverTimestamp(),
        codigoVinculo,
        codigoPaciente: codigoVinculo,
      };
      if (usuarioSnapshot.exists) {
        transaction.update(usuarioRef, {
          nome,
          telefone,
          email: emailNormalizado,
          perfil: "PACIENTE",
          ativo: true,
          codigoVinculo,
          codigoPaciente: codigoVinculo,
        });
      } else {
        transaction.create(usuarioRef, perfil);
      }
      return { codigoVinculo, codigoPaciente: codigoVinculo, nome, telefone, email: emailNormalizado, perfil: "PACIENTE" };
    });
    if (resultado) return resultado;
  }

  throw new ErroCadastroPaciente("resource-exhausted", "Não foi possível gerar o código. Tente novamente.");
}
