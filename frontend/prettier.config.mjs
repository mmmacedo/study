/** @type {import("prettier").Config} */
export default {
  // Ponto e vírgula obrigatório ao fim de statements — evita ASI (Automatic Semicolon Insertion)
  // bugs sutis em casos como arrays/funções no início de linha.
  semi: true,

  // Aspas simples em vez de duplas em strings JS/TS.
  // Exceção: strings que contêm aspas simples internas usam duplas automaticamente.
  singleQuote: true,

  // Tamanho do tab em espaços. 2 é o padrão do ecossistema JS/TS.
  tabWidth: 2,

  // Vírgula após o último elemento em objetos, arrays e parâmetros multi-linha.
  // "all" inclui parâmetros de função — exige ES5+ (padrão desde 2009).
  // Reduz diffs: adicionar/remover o último item mexe só naquela linha.
  trailingComma: 'all',

  // Quebra de linha após 100 colunas. Mais generoso que o padrão (80) para
  // acomodar JSX com props longas sem quebrar a leitura.
  printWidth: 100,

  // Parênteses obrigatórios em arrow functions de parâmetro único: (x) => x.
  // Consistência visual e facilita adicionar mais parâmetros ou anotações de tipo.
  arrowParens: 'always',
};
