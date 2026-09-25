export function dataLocalSaoPaulo(diasAdicionais = 0, referencia = new Date()): string {
  const partes = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(referencia);
  const campos = Object.fromEntries(partes.map(({ type, value }) => [type, value]));
  const data = new Date(Date.UTC(Number(campos.year), Number(campos.month) - 1, Number(campos.day) + diasAdicionais));
  return data.toISOString().slice(0, 10);
}
