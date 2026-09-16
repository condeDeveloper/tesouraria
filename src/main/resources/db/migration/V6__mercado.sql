create table cotacao (
    id          uuid            not null primary key,
    par         varchar(6)      not null,
    tipo        varchar(6)      not null,
    data        date            not null,
    taxa        numeric(19, 8)  not null,
    fonte       varchar(40)     not null,
    criado_em   timestamp       not null default current_timestamp,
    constraint uk_cotacao unique (par, tipo, data),
    constraint ck_cotacao_tipo check (tipo in ('SPOT', 'PTAX')),
    constraint ck_cotacao_taxa check (taxa > 0)
);

create index ix_cotacao_busca on cotacao (par, tipo, data desc);

create table curva_juros (
    id                uuid          not null primary key,
    nome              varchar(20)   not null,
    data_referencia   date          not null,
    convencao         varchar(12)   not null,
    criado_em         timestamp     not null default current_timestamp,
    constraint uk_curva unique (nome, data_referencia),
    constraint ck_curva_convencao check (convencao in ('EXP_252', 'LINEAR_360'))
);

create table vertice_curva (
    id          uuid            not null primary key,
    curva_id    uuid            not null references curva_juros (id),
    prazo_dias  integer         not null,
    taxa        numeric(12, 8)  not null,
    constraint uk_vertice unique (curva_id, prazo_dias),
    constraint ck_vertice_prazo check (prazo_dias > 0)
);

-- Dados de exemplo em 2026-09-15 para demonstração e testes
insert into cotacao (id, par, tipo, data, taxa, fonte) values
    (random_uuid(), 'USDBRL', 'PTAX', date '2026-09-11', 5.31240000, 'BCB'),
    (random_uuid(), 'USDBRL', 'PTAX', date '2026-09-14', 5.29870000, 'BCB'),
    (random_uuid(), 'USDBRL', 'PTAX', date '2026-09-15', 5.30510000, 'BCB'),
    (random_uuid(), 'USDBRL', 'SPOT', date '2026-09-15', 5.30800000, 'MESA'),
    (random_uuid(), 'EURBRL', 'PTAX', date '2026-09-15', 6.21330000, 'BCB'),
    (random_uuid(), 'EURBRL', 'SPOT', date '2026-09-15', 6.21900000, 'MESA'),
    (random_uuid(), 'EURUSD', 'SPOT', date '2026-09-15', 1.17150000, 'MESA');

-- Curva DI (taxa anual, base 252 dias úteis, capitalização exponencial)
insert into curva_juros (id, nome, data_referencia, convencao) values
    ('0e7c1f3a-0000-4000-8000-000000000001', 'DI', date '2026-09-15', 'EXP_252');
insert into vertice_curva (id, curva_id, prazo_dias, taxa) values
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000001', 1,   0.14650000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000001', 21,  0.14620000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000001', 63,  0.14480000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000001', 126, 0.14150000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000001', 252, 0.13600000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000001', 504, 0.13100000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000001', 756, 0.12900000);

-- Cupom cambial limpo USD (taxa anual, base 360 dias corridos, linear)
insert into curva_juros (id, nome, data_referencia, convencao) values
    ('0e7c1f3a-0000-4000-8000-000000000002', 'CUPOM_USD', date '2026-09-15', 'LINEAR_360');
insert into vertice_curva (id, curva_id, prazo_dias, taxa) values
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000002', 1,    0.04900000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000002', 30,   0.04950000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000002', 90,   0.05050000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000002', 180,  0.05200000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000002', 360,  0.05400000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000002', 720,  0.05600000),
    (random_uuid(), '0e7c1f3a-0000-4000-8000-000000000002', 1080, 0.05700000);
