const test = require("node:test");
const assert = require("node:assert/strict");
const { obterPlanosPadrao } = require("../lib/asaas/planos.js");

test("creates the two configured recurring plans", () => {
  assert.deepEqual(obterPlanosPadrao("19.90", "39.90"), [
    { id: "QUINZENAL", nome: "Quinzenal", ciclo: "QUINZENAL", dias: 15, valor: 19.9, ativo: true },
    { id: "MENSAL", nome: "Mensal", ciclo: "MENSAL", dias: 30, valor: 39.9, ativo: true },
  ]);
});

test("rejects missing, non-numeric, and non-positive plan prices", () => {
  for (const [quinzenal, mensal] of [[undefined, "20"], ["abc", "20"], ["0", "20"], ["20", "-1"]]) {
    assert.throws(() => obterPlanosPadrao(quinzenal, mensal), /valores numéricos positivos/);
  }
});
