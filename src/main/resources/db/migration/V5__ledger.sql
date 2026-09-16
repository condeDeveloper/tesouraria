create table conta_contabil (
    id              uuid          not null primary key,
    codigo          varchar(20)   not null,
    nome            varchar(120)  not null,
    tipo            varchar(12)   not null,
    natureza        varchar(8)    not null,
    moeda           varchar(3)    not null references moeda (codigo),
    contraparte_id  uuid          references contraparte (id),
    ativa           boolean       not null default true,
    criado_em       timestamp     not null default current_timestamp,
    constraint uk_conta_codigo unique (codigo),
    constraint ck_conta_tipo check (tipo in ('ATIVO', 'PASSIVO', 'PATRIMONIO', 'RECEITA', 'DESPESA')),
    constraint ck_conta_natureza check (natureza in ('DEVEDORA', 'CREDORA'))
);

create table lancamento (
    id                  uuid          not null primary key,
    chave_idempotencia  varchar(80)   not null,
    data_lancamento     date          not null,
    descricao           varchar(200)  not null,
    origem              varchar(30)   not null,
    referencia_id       uuid,
    criado_em           timestamp     not null default current_timestamp,
    criado_por          varchar(40)   not null,
    estornado_por       uuid          references lancamento (id),
    constraint uk_lancamento_chave unique (chave_idempotencia)
);

create index ix_lancamento_data on lancamento (data_lancamento);
create index ix_lancamento_referencia on lancamento (origem, referencia_id);

create table partida (
    id              uuid           not null primary key,
    lancamento_id   uuid           not null references lancamento (id),
    conta_id        uuid           not null references conta_contabil (id),
    tipo            varchar(7)     not null,
    valor           numeric(19, 4) not null,
    moeda           varchar(3)     not null,
    ordem           integer        not null,
    constraint ck_partida_tipo check (tipo in ('DEBITO', 'CREDITO')),
    constraint ck_partida_valor check (valor > 0)
);

create index ix_partida_conta on partida (conta_id);
create index ix_partida_lancamento on partida (lancamento_id);

-- Plano de contas mínimo da tesouraria
insert into conta_contabil (id, codigo, nome, tipo, natureza, moeda) values
    (random_uuid(), '1.1.01.BRL', 'Caixa e bancos BRL', 'ATIVO', 'DEVEDORA', 'BRL'),
    (random_uuid(), '1.1.01.USD', 'Caixa e bancos USD', 'ATIVO', 'DEVEDORA', 'USD'),
    (random_uuid(), '1.1.01.EUR', 'Caixa e bancos EUR', 'ATIVO', 'DEVEDORA', 'EUR'),
    (random_uuid(), '1.2.01.BRL', 'Câmbio a receber BRL', 'ATIVO', 'DEVEDORA', 'BRL'),
    (random_uuid(), '1.2.01.USD', 'Câmbio a receber USD', 'ATIVO', 'DEVEDORA', 'USD'),
    (random_uuid(), '1.2.01.EUR', 'Câmbio a receber EUR', 'ATIVO', 'DEVEDORA', 'EUR'),
    (random_uuid(), '1.3.01.BRL', 'Derivativos - ajuste positivo BRL', 'ATIVO', 'DEVEDORA', 'BRL'),
    (random_uuid(), '2.1.01.BRL', 'Câmbio a entregar BRL', 'PASSIVO', 'CREDORA', 'BRL'),
    (random_uuid(), '2.1.01.USD', 'Câmbio a entregar USD', 'PASSIVO', 'CREDORA', 'USD'),
    (random_uuid(), '2.1.01.EUR', 'Câmbio a entregar EUR', 'PASSIVO', 'CREDORA', 'EUR'),
    (random_uuid(), '2.3.01.BRL', 'Derivativos - ajuste negativo BRL', 'PASSIVO', 'CREDORA', 'BRL'),
    (random_uuid(), '3.1.01.BRL', 'Patrimônio BRL', 'PATRIMONIO', 'CREDORA', 'BRL'),
    (random_uuid(), '4.1.01.BRL', 'Receita de câmbio BRL', 'RECEITA', 'CREDORA', 'BRL'),
    (random_uuid(), '4.2.01.BRL', 'Resultado com derivativos BRL', 'RECEITA', 'CREDORA', 'BRL'),
    (random_uuid(), '5.1.01.BRL', 'Despesa de câmbio BRL', 'DESPESA', 'DEVEDORA', 'BRL'),
    (random_uuid(), '5.9.01.BRL', 'Variação cambial BRL', 'DESPESA', 'DEVEDORA', 'BRL');
