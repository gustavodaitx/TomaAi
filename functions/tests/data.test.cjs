const test = require("node:test");
const assert = require("node:assert/strict");
const { dataLocalSaoPaulo } = require("../lib/utils/data.js");

test("uses São Paulo local date across the UTC day boundary", () => {
  const referencia = new Date("2024-02-29T02:00:00.000Z");
  assert.equal(dataLocalSaoPaulo(0, referencia), "2024-02-28");
  assert.equal(dataLocalSaoPaulo(1, referencia), "2024-02-29");
});
