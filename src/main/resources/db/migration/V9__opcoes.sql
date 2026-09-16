insert into conta_contabil (id, codigo, nome, tipo, natureza, moeda) values
    (random_uuid(), '1.3.02.BRL', 'Opções compradas - prêmios e ajustes BRL', 'ATIVO', 'DEVEDORA', 'BRL'),
    (random_uuid(), '2.3.02.BRL', 'Opções lançadas - prêmios e ajustes BRL', 'PASSIVO', 'CREDORA', 'BRL');

create table opcao_cambio (
    id                  uuid            not null primary key,
    numero              varchar(20)     not null,
    contraparte_id      uuid            not null references contraparte (id),
    par                 varchar(6)      not null,
    tipo                varchar(4)      not null,
    posicao             varchar(8)      not null,
    notional            numeric(19, 4)  not null,
    strike              numeric(19, 8)  not null,
    premio              numeric(19, 4)  not null,
    premio_teorico      numeric(19, 4),
    volatilidade        numeric(12, 8)  not null,
    data_negociacao     date            not null,
    data_vencimento     date            not null,
    data_liquidacao     date            not null,
    situacao            varchar(10)     not null,
    taxa_fixing         numeric(19, 8),
    payoff              numeric(19, 4),
    criado_por          varchar(40)     not null,
    criado_em           timestamp       not null default current_timestamp,
    encerrado_em        timestamp,
    constraint uk_opcao_numero unique (numero),
    constraint ck_opcao_tipo check (tipo in ('CALL', 'PUT')),
    constraint ck_opcao_posicao check (posicao in ('COMPRADA', 'LANCADA')),
    constraint ck_opcao_situacao check (situacao in ('ABERTA', 'EXERCIDA', 'EXPIRADA', 'CANCELADA')),
    constraint ck_opcao_valores check (notional > 0 and strike > 0 and premio >= 0 and volatilidade > 0),
    constraint ck_opcao_datas check (data_negociacao < data_vencimento and data_vencimento <= data_liquidacao)
);

create index ix_opcao_situacao on opcao_cambio (situacao, data_vencimento);

create table marcacao_opcao (
    id              uuid            not null primary key,
    opcao_id        uuid            not null references opcao_cambio (id),
    data            date            not null,
    spot            numeric(19, 8)  not null,
    forward         numeric(19, 8)  not null,
    volatilidade    numeric(12, 8)  not null,
    prazo_anos      numeric(12, 8)  not null,
    valor_unitario  numeric(19, 8)  not null,
    valor           numeric(19, 4)  not null,
    delta           numeric(19, 8)  not null,
    gamma           numeric(19, 10) not null,
    vega            numeric(19, 6)  not null,
    theta           numeric(19, 6)  not null,
    rho             numeric(19, 6)  not null,
    lancamento_id   uuid,
    criado_em       timestamp       not null default current_timestamp,
    constraint uk_marcacao_opcao unique (opcao_id, data)
);

-- Volatilidade implícita ATM de USDBRL por prazo (dias corridos), guardada como curva
insert into curva_juros (id, nome, data_referencia, convencao) values
    ('0e7c1f3a-0000-4000-8000-000000000003', 'VOL_USD', date '2026-09-15', 'LINEAR_360');
insert into vertice_curva (id, curva_id, prazo_dias, taxa) values
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000003', 7,   0.12500000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000003', 30,  0.13200000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000003', 90,  0.14100000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000003', 180, 0.14800000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000003', 360, 0.15300000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000003', 720, 0.15800000);
