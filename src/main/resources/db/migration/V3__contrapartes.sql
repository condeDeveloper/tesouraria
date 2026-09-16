create table contraparte (
    id              uuid          not null primary key,
    nome            varchar(120)  not null,
    documento       varchar(14)   not null,
    tipo            varchar(12)   not null,
    situacao        varchar(10)   not null default 'ATIVA',
    email           varchar(120),
    criado_em       timestamp     not null default current_timestamp,
    atualizado_em   timestamp     not null default current_timestamp,
    constraint uk_contraparte_documento unique (documento),
    constraint ck_contraparte_tipo check (tipo in ('PF', 'PJ', 'INSTITUICAO')),
    constraint ck_contraparte_situacao check (situacao in ('ATIVA', 'BLOQUEADA', 'INATIVA'))
);
