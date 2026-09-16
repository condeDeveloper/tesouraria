-- Contas de câmbio: a liquidar (comprado/vendido) e posição por moeda
update conta_contabil set nome = 'Câmbio comprado a liquidar BRL' where codigo = '1.2.01.BRL';
update conta_contabil set nome = 'Câmbio comprado a liquidar USD' where codigo = '1.2.01.USD';
update conta_contabil set nome = 'Câmbio comprado a liquidar EUR' where codigo = '1.2.01.EUR';
update conta_contabil set nome = 'Câmbio vendido a liquidar BRL' where codigo = '2.1.01.BRL';
update conta_contabil set nome = 'Câmbio vendido a liquidar USD' where codigo = '2.1.01.USD';
update conta_contabil set nome = 'Câmbio vendido a liquidar EUR' where codigo = '2.1.01.EUR';

insert into conta_contabil (id, codigo, nome, tipo, natureza, moeda) values
    (random_uuid(), '2.2.01.BRL', 'Posição de câmbio BRL', 'PASSIVO', 'CREDORA', 'BRL'),
    (random_uuid(), '2.2.01.USD', 'Posição de câmbio USD', 'PASSIVO', 'CREDORA', 'USD'),
    (random_uuid(), '2.2.01.EUR', 'Posição de câmbio EUR', 'PASSIVO', 'CREDORA', 'EUR');

-- Numeração sequencial por prefixo (ex.: CAM-2026)
create table numerador (
    chave   varchar(20)  not null primary key,
    ultimo  bigint       not null default 0
);

create table operacao_cambio (
    id                  uuid            not null primary key,
    numero              varchar(20)     not null,
    contraparte_id      uuid            not null references contraparte (id),
    par                 varchar(6)      not null,
    lado                varchar(6)      not null,
    valor_base          numeric(19, 4)  not null,
    moeda_base          varchar(3)      not null,
    taxa                numeric(19, 8)  not null,
    valor_cotado        numeric(19, 4)  not null,
    moeda_cotada        varchar(3)      not null,
    taxa_referencia     numeric(19, 8),
    data_negociacao     date            not null,
    data_liquidacao     date            not null,
    situacao            varchar(10)     not null,
    criado_por          varchar(40)     not null,
    criado_em           timestamp       not null default current_timestamp,
    liquidado_em        timestamp,
    cancelado_em        timestamp,
    constraint uk_operacao_cambio_numero unique (numero),
    constraint ck_operacao_cambio_lado check (lado in ('COMPRA', 'VENDA')),
    constraint ck_operacao_cambio_situacao check (situacao in ('ABERTA', 'LIQUIDADA', 'CANCELADA')),
    constraint ck_operacao_cambio_valores check (valor_base > 0 and valor_cotado > 0 and taxa > 0)
);

create index ix_operacao_cambio_situacao on operacao_cambio (situacao, data_liquidacao);
create index ix_operacao_cambio_contraparte on operacao_cambio (contraparte_id);
