insert into conta_contabil (id, codigo, nome, tipo, natureza, moeda) values
    (random_uuid(), '5.2.01.BRL', 'Perda com derivativos BRL', 'DESPESA', 'DEVEDORA', 'BRL');

create table contrato_ndf (
    id                  uuid            not null primary key,
    numero              varchar(20)     not null,
    contraparte_id      uuid            not null references contraparte (id),
    par                 varchar(6)      not null,
    lado                varchar(6)      not null,
    notional            numeric(19, 4)  not null,
    moeda_base          varchar(3)      not null,
    moeda_liquidacao    varchar(3)      not null,
    taxa_termo          numeric(19, 8)  not null,
    forward_referencia  numeric(19, 8),
    data_negociacao     date            not null,
    data_fixing         date            not null,
    data_liquidacao     date            not null,
    situacao            varchar(10)     not null,
    taxa_fixing         numeric(19, 8),
    ajuste              numeric(19, 4),
    criado_por          varchar(40)     not null,
    criado_em           timestamp       not null default current_timestamp,
    fixado_em           timestamp,
    liquidado_em        timestamp,
    cancelado_em        timestamp,
    constraint uk_ndf_numero unique (numero),
    constraint ck_ndf_lado check (lado in ('COMPRA', 'VENDA')),
    constraint ck_ndf_situacao check (situacao in ('ABERTO', 'FIXADO', 'LIQUIDADO', 'CANCELADO')),
    constraint ck_ndf_valores check (notional > 0 and taxa_termo > 0),
    constraint ck_ndf_datas check (data_fixing <= data_liquidacao and data_negociacao <= data_fixing)
);

create index ix_ndf_situacao on contrato_ndf (situacao, data_liquidacao);
create index ix_ndf_contraparte on contrato_ndf (contraparte_id);

create table marcacao_mercado (
    id                  uuid            not null primary key,
    contrato_id         uuid            not null references contrato_ndf (id),
    data                date            not null,
    spot                numeric(19, 8)  not null,
    forward_teorico     numeric(19, 8)  not null,
    fator_desconto      numeric(19, 12) not null,
    valor_presente      numeric(19, 4)  not null,
    lancamento_id       uuid,
    criado_em           timestamp       not null default current_timestamp,
    constraint uk_mtm unique (contrato_id, data)
);

create index ix_mtm_contrato on marcacao_mercado (contrato_id, data desc);
