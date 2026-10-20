# Parte 9 da API: fotos guardadas no banco (SÓ PROPOSTA, sem código)

Decisão do Yago em 19/10/2026: por enquanto as fotos ficam DENTRO do banco PostgreSQL. Motivo: o disco da Render é apagado a cada deploy e não vamos criar serviço externo agora. A decisão antiga continua: NÃO reduzir a foto.

## Regra de trabalho
Só proponha. Não codifique, não rode SQL, não mexa em arquivo nenhum. Responda com o plano e as dúvidas. O coordenador aprova e só depois você faz, uma etapa por vez, com relatório no formato de sempre.

## O que o coordenador quer
1. **Envio:** uma rota única, separada do evento: `POST /fotos`, multipart (arquivo + campos).
   Campos: `diarioId` (obrigatório), `tipo`, `ocorrenciaId` (opcional), `abastecimentoId` (opcional), `idLocal` (UUID), `ocorridoEm` (hora em que o celular tirou a foto), `enviadoEm`.
   Mesmas regras dos eventos: HorarioEvento, `DIARIO_ASSINADO` (409), idempotência pelo `idLocal` (mesmo idLocal = 200 com a mesma foto, sem duplicar), `ID_LOCAL_EM_USO`, `REENVIO_SIMULTANEO`.
2. **Por que separada do evento:** a foto tem vários MB. Se viesse junto, uma foto que falha travaria o abastecimento. O app manda o evento primeiro, recebe o id e depois manda a foto com `abastecimentoId` ou `ocorrenciaId`.
3. **Leitura:** `GET /fotos/{id}` devolve a imagem (Content-Type certo, cache). `GET /diarios/{id}/fotos` devolve só os metadados (id, tipo, ocorridoEm, tamanho, vínculo), SEM os bytes. Nunca devolver bytes em lista (lição da foto de perfil).
4. **Permissão:** motorista só envia e vê foto dos próprios diários (ControleAcesso); gestor vê todas. Entra na matriz da Parte 5 (POST só motorista; GET os dois).

## Pontos para você decidir e propor
a. **Como guardar:** coluna `bytea` (binário, sem os 33% a mais do base64) na tabela `fotos` que já existe, ou base64 em `text` (padrão de `usuarios.foto` e `manutencoes.foto_nota_fiscal`). Inclinação do coordenador: `bytea`. Diga o que muda no JPA/consulta nativa e como garantir que nenhuma consulta de lista traga o conteúdo.
b. **`caminho_arquivo` é NOT NULL:** proponha o que fazer (o coordenador prefere tornar opcional).
c. **Tipos:** o enum `tipo_foto` tem medidor_abertura, medidor_fechamento, ocorrencia, livre. Faltam cupom fiscal e bomba do comboio. Proponha (valores novos no enum, ou coluna `abastecimento_id` e tipo livre) e o cuidado com `ALTER TYPE ... ADD VALUE`.
d. **SQL aditivo:** colunas necessárias (conteudo, tipo_mime, tamanho_bytes, hash_sha256, id_local com índice único parcial, ocorrido_em, recebida_em, registrado_por, abastecimento_id), no formato de sempre: rodar antes do deploy, com rollback.
e. **Limites:** o Spring tem limite padrão de 1 MB por arquivo no multipart. Proponha o limite (ex.: 10 MB) e as variáveis de ambiente. Validar o tipo (só JPEG e PNG) olhando os bytes, não só o nome. A Render gratuita tem pouca memória: evitar ler vários arquivos grandes ao mesmo tempo. Proponha o código de erro para foto grande demais.
f. **Assinatura:** o hash do diário assinado passa a cobrir as fotos? Descreva o custo de cada opção (item 14 da lista). Inclinação do coordenador: guardar o sha256 de cada foto agora, mas não mudar o hash da assinatura nesta parte.
g. **Espaço:** o banco gratuito é pequeno. Estime quantas fotos cabem, proponha uma consulta ou rota para ver o tamanho usado, e como trocar depois por armazenamento externo sem mudar app nem front (as rotas são o contrato; o armazenamento fica escondido atrás delas).
h. **Web:** o `<img src>` do navegador não manda o `Authorization`. Diga o que o front precisa fazer (buscar a imagem com o token e mostrar como blob) ou proponha outra saída.
i. **Testes pelo Swagger:** lista curta (envio, reenvio com o mesmo idLocal, foto de diário assinado, foto grande demais, tipo inválido, leitura).

## Extra pequeno (item 35): rota de motivos de uso
O app tem um campo de texto "motivo" na abertura do diário, mas a API só aceita `motivoUsoId` (id da tabela `motivos_uso`) e não existe rota para listar os motivos. Proponha um `GET /motivos-uso` (id e descrição dos motivos ativos), com permissão para gestor e motorista. Se a tabela precisar de ajuste, diga qual.

## O que devolver
Plano por etapas, SQL proposto (sem rodar), variáveis de ambiente, impacto no app e no front, riscos e o que você NÃO vai fazer. Depois pare e espere a aprovação.