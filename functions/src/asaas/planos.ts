export interface PlanoPadrao {
  id: "QUINZENAL" | "MENSAL";
  nome: string;
  ciclo: "QUINZENAL" | "MENSAL";
  dias: number;
  valor: number;
  ativo: true;
}

export function obterPlanosPadrao(precoQuinzenal?: string, precoMensal?: string): PlanoPadrao[] {
  const quinzenal = Number(precoQuinzenal);
  const mensal = Number(precoMensal);
  if (!Number.isFinite(quinzenal) || quinzenal <= 0 || !Number.isFinite(mensal) || mensal <= 0) {
    throw new Error("PRECO_QUINZENAL e PRECO_MENSAL devem conter valores numéricos positivos.");
  }
  return [
    { id: "QUINZENAL", nome: "Quinzenal", ciclo: "QUINZENAL", dias: 15, valor: quinzenal, ativo: true },
    { id: "MENSAL", nome: "Mensal", ciclo: "MENSAL", dias: 30, valor: mensal, ativo: true },
  ];
}
