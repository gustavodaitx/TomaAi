import axios from "axios";

export async function criarClienteNoAsaas(
  nome: string,
  email: string,
  cpfCnpj?: string
): Promise<string> {
  const asaasUrl = process.env.ASAAS_URL || "https://api-sandbox.asaas.com/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";

  if (!apiKey) {
    throw new Error("ASAAS_API_KEY não configurada no ambiente do backend.");
  }

  try {
    const response = await axios.post(
      `${asaasUrl}/customers`,
      {
        name: nome,
        email: email,
        cpfCnpj: cpfCnpj || undefined,
        notificationDisabled: false,
      },
      {
        headers: {
          access_token: apiKey,
          "Content-Type": "application/json",
        },
      }
    );

    return response.data.id;
  } catch (error: any) {
    console.error("Erro ao criar cliente no Asaas:", error?.response?.data || error.message);
    throw new Error(
      error?.response?.data?.errors?.[0]?.description ||
        "Falha ao cadastrar cliente no gateway Asaas."
    );
  }
}
