const test = require("node:test");
const assert = require("node:assert/strict");
const {
  criarPerfilPaciente,
  ErroCadastroPaciente,
  gerarCodigoVinculo,
  obterIdentidadeAutenticada,
} = require("../lib/usuarios/cadastroPaciente.js");

class FakeFirestore {
  constructor() {
    this.documents = new Map();
    this.pending = Promise.resolve();
    this.transactionCount = 0;
  }

  collection(name) {
    return { doc: (id) => ({ path: `${name}/${id}` }) };
  }

  async runTransaction(callback) {
    let release;
    const previous = this.pending;
    this.pending = new Promise((resolve) => { release = resolve; });
    await previous;
    this.transactionCount += 1;
    const writes = [];
    const transaction = {
      get: async (ref) => {
        const value = this.documents.get(ref.path);
        return { exists: value !== undefined, data: () => value && structuredClone(value) };
      },
      create: (ref, data) => writes.push({ type: "create", path: ref.path, data }),
      update: (ref, data) => writes.push({ type: "update", path: ref.path, data }),
    };
    try {
      const result = await callback(transaction);
      for (const write of writes) {
        if (write.type === "create" && this.documents.has(write.path)) {
          throw new Error(`Document already exists: ${write.path}`);
        }
        if (write.type === "update" && !this.documents.has(write.path)) {
          throw new Error(`Document does not exist: ${write.path}`);
        }
      }
      for (const write of writes) {
        const previousData = this.documents.get(write.path) || {};
        this.documents.set(write.path, { ...previousData, ...structuredClone(write.data) });
      }
      return result;
    } finally {
      release();
    }
  }
}

const dados = { nome: "Maria Silva", telefone: "11999999999" };

test("generates a random link code in TMA-XXXXXX format", () => {
  assert.match(gerarCodigoVinculo(), /^TMA-[A-HJ-NP-Z2-9]{6}$/);
});

test("requires an authenticated UID and email", () => {
  assert.throws(() => obterIdentidadeAutenticada(null), (error) =>
    error instanceof ErroCadastroPaciente && error.codigo === "unauthenticated");
  assert.deepEqual(obterIdentidadeAutenticada({ uid: "uid-1", token: { email: "maria@example.com" } }), {
    uid: "uid-1",
    email: "maria@example.com",
  });
});

test("rejects invalid patient data before opening a Firestore transaction", async () => {
  const firestore = new FakeFirestore();
  await assert.rejects(
    criarPerfilPaciente("uid-1", "maria@example.com", { ...dados, telefone: "123" }, firestore),
    (error) => error instanceof ErroCadastroPaciente && error.codigo === "invalid-argument"
  );
  assert.equal(firestore.transactionCount, 0);
  await assert.rejects(
    criarPerfilPaciente("uid-1", "maria@example.com", null, firestore),
    (error) => error instanceof ErroCadastroPaciente && error.codigo === "invalid-argument"
  );
});

test("creates the patient profile and unique code reservation atomically", async () => {
  const firestore = new FakeFirestore();
  const profile = await criarPerfilPaciente("uid-1", "maria@example.com", dados, firestore, () => "TMA-AAAAAA");

  assert.deepEqual(profile, {
    codigoVinculo: "TMA-AAAAAA",
    nome: "Maria Silva",
    telefone: "11999999999",
    email: "maria@example.com",
    perfil: "PACIENTE",
  });
  assert.equal(firestore.documents.get("usuarios/uid-1").codigoVinculo, "TMA-AAAAAA");
  assert.equal(firestore.documents.get("codigos_vinculo_pacientes/TMA-AAAAAA").pacienteUid, "uid-1");
});

test("repeated calls for one UID return the existing profile and code", async () => {
  const firestore = new FakeFirestore();
  let generated = 0;
  const generate = () => `TMA-${String.fromCharCode(65 + generated++).repeat(6)}`;
  const first = await criarPerfilPaciente("uid-1", "maria@example.com", dados, firestore, generate);
  const second = await criarPerfilPaciente("uid-1", "outro@example.com", { nome: "Outro nome", telefone: "11988887777" }, firestore, generate);

  assert.deepEqual(second, first);
  assert.equal(generated, 1);
  assert.equal([...firestore.documents.keys()].filter((key) => key.startsWith("codigos_vinculo_pacientes/")).length, 1);
});

test("uses distinct codes for different patients", async () => {
  const firestore = new FakeFirestore();
  const codes = ["TMA-AAAAAA", "TMA-BBBBBB"];
  const first = await criarPerfilPaciente("uid-1", "a@example.com", dados, firestore, () => codes.shift());
  const second = await criarPerfilPaciente("uid-2", "b@example.com", dados, firestore, () => codes.shift());

  assert.notEqual(first.codigoVinculo, second.codigoVinculo);
  assert.equal(firestore.documents.get("codigos_vinculo_pacientes/TMA-AAAAAA").pacienteUid, "uid-1");
  assert.equal(firestore.documents.get("codigos_vinculo_pacientes/TMA-BBBBBB").pacienteUid, "uid-2");
});

test("retries after a reserved-code collision", async () => {
  const firestore = new FakeFirestore();
  firestore.documents.set("codigos_vinculo_pacientes/TMA-AAAAAA", { pacienteUid: "outro-uid" });
  const codes = ["TMA-AAAAAA", "TMA-BBBBBB"];
  const profile = await criarPerfilPaciente("uid-1", "a@example.com", dados, firestore, () => codes.shift());

  assert.equal(profile.codigoVinculo, "TMA-BBBBBB");
});

test("completes an incomplete default patient profile", async () => {
  const firestore = new FakeFirestore();
  firestore.documents.set("usuarios/uid-1", {
    id: "uid-1",
    nome: "Usuário TomaAí",
    email: "a@example.com",
    perfil: "PACIENTE",
    ativo: true,
    criadoEm: "preserve-this-value",
  });
  const profile = await criarPerfilPaciente("uid-1", "a@example.com", dados, firestore, () => "TMA-CCCCCC");

  assert.equal(profile.codigoVinculo, "TMA-CCCCCC");
  assert.equal(firestore.documents.get("usuarios/uid-1").telefone, dados.telefone);
  assert.equal(firestore.documents.get("usuarios/uid-1").criadoEm, "preserve-this-value");
  assert.equal([...firestore.documents.keys()].filter((key) => key.startsWith("codigos_vinculo_pacientes/")).length, 1);
});

test("concurrent calls for the same UID create one profile and one code", async () => {
  const firestore = new FakeFirestore();
  let generated = 0;
  const generate = () => `TMA-${String.fromCharCode(65 + generated++).repeat(6)}`;
  const profiles = await Promise.all([
    criarPerfilPaciente("uid-1", "maria@example.com", dados, firestore, generate),
    criarPerfilPaciente("uid-1", "maria@example.com", dados, firestore, generate),
  ]);

  assert.equal(profiles[0].codigoVinculo, profiles[1].codigoVinculo);
  assert.equal(generated, 1);
  assert.equal([...firestore.documents.keys()].filter((key) => key.startsWith("codigos_vinculo_pacientes/")).length, 1);
});
