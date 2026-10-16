const test = require("node:test");
const assert = require("node:assert/strict");
const { limparDadosMedicacao } = require("../lib/usuarios/limparDadosMedicacao.js");

class FakeFirestore {
  constructor(seed = {}) {
    this.docs = new Map();
    this.queryCalls = new Map();
    this.failQueryAt = null;
    for (const [collection, documents] of Object.entries(seed)) {
      for (const [id, data] of Object.entries(documents)) {
        this.docs.set(`${collection}/${id}`, structuredClone(data));
      }
    }
  }

  collection(name) {
    return new FakeCollection(this, name);
  }

  batch() {
    const refs = [];
    return {
      delete: (ref) => refs.push(ref),
      commit: async () => refs.forEach((ref) => this.docs.delete(ref.path)),
    };
  }
}

class FakeCollection {
  constructor(db, name) {
    this.db = db;
    this.name = name;
  }

  where(field, _operator, value) {
    return new FakeQuery(this.db, this.name, field, value);
  }

  doc(id) {
    return new FakeRef(this.db, this.name, id);
  }
}

class FakeQuery {
  constructor(db, collection, field, value, max = Infinity) {
    Object.assign(this, { db, collection, field, value, max });
  }

  limit(max) {
    return new FakeQuery(this.db, this.collection, this.field, this.value, max);
  }

  async get() {
    const key = `${this.collection}:${this.field}:${this.value}`;
    const calls = (this.db.queryCalls.get(key) || 0) + 1;
    this.db.queryCalls.set(key, calls);
    if (this.db.failQueryAt === `${key}:${calls}`) throw new Error("permission denied");
    const docs = [...this.db.docs.entries()]
      .filter(([path, data]) => path.startsWith(`${this.collection}/`) && data[this.field] === this.value)
      .slice(0, this.max)
      .map(([path, data]) => new FakeSnapshot(this.db, path, data));
    return { docs, empty: docs.length === 0 };
  }
}

class FakeRef {
  constructor(db, collection, id) {
    Object.assign(this, { db, collection, id, path: `${collection}/${id}` });
  }

  async get() {
    const data = this.db.docs.get(this.path);
    return new FakeSnapshot(this.db, this.path, data);
  }
}

class FakeSnapshot {
  constructor(db, path, data) {
    Object.assign(this, { id: path.split("/").at(-1), ref: new FakeRef(db, ...path.split("/")), exists: data !== undefined });
  }
}

test("deletes and verifies only the authenticated patient's medication data", async () => {
  const db = new FakeFirestore({
    medicamentos: { medA: { usuarioId: "pacienteA" }, medB: { usuarioId: "pacienteB" } },
    horarios_medicamentos: { horaA: { usuarioId: "pacienteA" } },
    doses: { doseA: { usuarioId: "pacienteA" }, doseB: { usuarioId: "pacienteB" } },
    alertas: {
      alertOwned: { usuarioId: "pacienteA" },
      doseA: { status: "legacy-alert-without-owner" },
      doseB: { status: "other-patient-alert" },
    },
  });

  const removed = await limparDadosMedicacao("pacienteA", db);

  assert.deepEqual(removed, {
    medicamentos: 1,
    horarios_medicamentos: 1,
    doses: 1,
    alertas: 2,
  });
  assert.equal(db.docs.has("medicamentos/medA"), false);
  assert.equal(db.docs.has("doses/doseA"), false);
  assert.equal(db.docs.has("alertas/doseA"), false);
  assert.equal(db.docs.has("medicamentos/medB"), true);
  assert.equal(db.docs.has("doses/doseB"), true);
  assert.equal(db.docs.has("alertas/doseB"), true);
});

test("does not report success when a batch fails", async () => {
  const db = new FakeFirestore({ medicamentos: { medA: { usuarioId: "pacienteA" } } });
  db.batch = () => ({ delete: () => {}, commit: async () => { throw new Error("network failure"); } });

  await assert.rejects(limparDadosMedicacao("pacienteA", db), /Falha ao excluir\/verificar a coleção medicamentos/);
  assert.equal(db.docs.has("medicamentos/medA"), true);
});

test("does not report success when the post-delete server validation fails", async () => {
  const db = new FakeFirestore({ medicamentos: { medA: { usuarioId: "pacienteA" } } });
  db.failQueryAt = "medicamentos:usuarioId:pacienteA:2";

  await assert.rejects(limparDadosMedicacao("pacienteA", db), /Falha ao validar a coleção medicamentos/);
  assert.equal(db.docs.has("medicamentos/medA"), false);
});
