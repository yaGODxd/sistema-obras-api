A Parte 2 está pronta: 39 testes passaram, 1 desligado de propósito, e o jar foi gerado de novo e mudou. Não fiz commit nem push e não conectei ao banco.

**Ficou um pedaço pendente:** os limites de tamanho dos textos. Nem as entidades nem o código dizem o tamanho de cada coluna no banco. Para isso preciso que você rode a consulta 4 de `docs/migracoes.sql`; ela só lê, não altera nada. Até lá, um texto longo demais é recusado pelo próprio banco, e a API responde `{"erro":"Algum texto passou do tamanho máximo permitido."}`.

## Relatório da Parte 2

**1. O que mudou**
- **11 DTOs de entrada:** a API recusa texto obrigatório em branco e número negativo.
  - Números: medidor (abrir/fechar diário e veículo), litros, valor, custo de mão de obra, quantidade e valor da peça. Vale também para as peças dentro de `POST /manutencoes`.
  - Perfil no cadastro: só `gestor` ou `motorista`. Isso evita que o banco recuse depois, como aconteceu com o status do veículo.
- **9 controllers:** passaram a ativar essa validação.
  - O login ficou de fora de propósito: validar ali mudaria o 401 para 400.
- **`UsuarioService` e `UsuarioRepository`:** antes de gravar, confere se o CPF já existe, comparando só os números (com ou sem pontos e traço).
  - Também ganhou o interruptor `app.validar-cpf-digitos`, que vem desligado.
- **`ValidadorCpf.kt` (novo):** a conta dos dígitos verificadores do CPF.
- **`VeiculoService` e `VeiculoRepository`:** antes de gravar e na edição, confere se a placa já existe.
  - Ignora traço, espaço e maiúsculas/minúsculas.
  - Placa vazia não é conferida e passa a ser gravada como vazia (`null`) em vez de `""`.
  - Na edição, o próprio veículo não conta como repetido.
- **`TratadorDeErros`:** mensagem no formato "Campo 'litros' não pode ser negativo." e mensagens próprias para texto longo demais e data inválida.
- **`application.properties`:** novo `app.validar-cpf-digitos`, desligado por padrão.
- **`docs/migracoes.sql` (novo):** só blocos comentados, ver item 2.
- **Testes:** `ValidacoesCadastroTest` (6, novo) e 5 testes novos em `ErrosDasRotasTest`.
- **`CLAUDE.md` e `PENDENCIAS`:** item 3 marcado como feito, com a pendência dos tamanhos.

**2. SQL antes do deploy:** nenhum obrigatório. Em `docs/migracoes.sql` ficaram, comentados:
- consultas que só leem, para ver os índices únicos que já existem e procurar CPF, placa e login repetidos;
- a sugestão dos índices únicos (CPF só pelos números; placa ignorando vazia e veículo inativo), para criar só se as buscas por repetidos voltarem vazias;
- a consulta 4, dos tamanhos de coluna, para você me mandar o resultado.

**3. Variáveis no Render:** nenhuma nova obrigatória. `APP_VALIDAR_CPF_DIGITOS=true` liga a conferência dos dígitos do CPF quando você quiser, sem novo deploy de código.

**4. Testes e jar**
- `.\gradlew.bat test`: BUILD SUCCESSFUL, 39 passaram, 0 falhas, 1 desligado.
- `.\gradlew.bat bootJar`: BUILD SUCCESSFUL. **O jar mudou: sim.**

**5. O que testar**
- `POST /abastecimentos` com `"litros": -1` → 400 `{"erro":"Campo 'litros' não pode ser negativo."}`
- `POST /diarios/fechar/{login}` com `"medidorFinal": -5` → 400 `{"erro":"Campo 'medidorFinal' não pode ser negativo."}`
- `POST /auth/cadastrar` com um CPF que já existe (com ou sem máscara) → 400 `{"erro":"Já existe um usuário com este CPF."}`
- `POST /auth/cadastrar` com `"perfil": "admin"` → 400 `{"erro":"Campo 'perfil' deve ser gestor ou motorista."}`
- `POST /veiculos` com a placa de outro veículo ativo, escrita em minúsculas e com traço → 400 `{"erro":"Já existe um veículo com esta placa."}`
- `PUT /veiculos/{id}` mantendo a mesma placa → 200, como antes.
- Cadastrar dois veículos sem placa → os dois aceitos.
- `POST /postos` com `"nome": "  "` → 400 `{"erro":"Campo 'nome' é obrigatório."}`

**6. Impacto no front e no app**
- **Muda status (é o objetivo da parte, mas confirme):** em `POST /mecanicos`, `POST /postos` e `POST /checklist/itens`, nome ou descrição em branco antes gravava e respondia 200. Agora responde 400.
- **Front:** confira se o cadastro de usuário envia o perfil em minúsculas (`gestor`/`motorista`).
- **Telas de veículo:** veículo sem placa passa a vir com `placa: null` em vez de `""`. Confira se alguma tela quebra com isso.
- **App:** se alguma tela mandar `tipoCombustivel`, `tipo` ou `descricao` em branco, agora recebe 400 com a mensagem. Antes gravava vazio.

**7. Como desfazer:** sem SQL, basta `git revert` do commit da Parte 2 e o deploy. Para só desligar a conferência dos dígitos do CPF, a variável já basta.

**8. Achados fora do escopo (não mexi)**
- **Decisão sua: placa de veículo inativo.** A API deixa cadastrar uma placa que pertence a um veículo inativo, porque não existe rota para reativar veículo. Se preferir bloquear, é uma linha.
- **Litros zero e quantidade zero são aceitos.** Recusei só negativo, como o plano pede. Recusar zero é uma linha por campo.
- **Medidor final menor que o inicial não é recusado.** O percorrido fica negativo. Sugiro recusar na Parte 3 ou 5, mas antes você precisa decidir se existe troca de hodômetro.
- **Status de manutenção ainda aceita qualquer texto:** `PUT /manutencoes/{id}/status` ainda aceita qualquer valor. Fica para a Parte 3, como já está no plano.
- **CPF só no cadastro:** não existe rota para editar CPF, então a conferência de repetido só acontece no cadastro.

**Ordem de deploy:** rodar o SQL (nenhum obrigatório), commitar com o jar e `docs/migracoes.sql`, dar push, esperar o Render e testar o item 5. Antes disso, se ainda não fez, confira no Render a variável `SPRING_JPA_HIBERNATE_DDL_AUTO` da Parte 1.

Paro aqui. Para continuar, me mande o resultado da consulta 4 (para eu fechar os limites de tamanho) ou escreva "pode começar a parte 3".
