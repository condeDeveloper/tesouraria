create table limite_contraparte (
    contraparte_id  uuid            not null primary key references contraparte (id),
    limite_brl      numeric(19, 2)  not null,
    atualizado_em   timestamp       not null default current_timestamp,
    atualizado_por  varchar(40)     not null,
    constraint ck_limite_positivo check (limite_brl >= 0)
);
