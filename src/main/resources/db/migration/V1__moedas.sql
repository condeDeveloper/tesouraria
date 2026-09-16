create table moeda (
    codigo          varchar(3)   not null primary key,
    nome            varchar(60)  not null,
    casas_decimais  integer      not null default 2,
    ativa           boolean      not null default true,
    criado_em       timestamp    not null default current_timestamp
);

insert into moeda (codigo, nome, casas_decimais) values
    ('BRL', 'Real brasileiro', 2),
    ('USD', 'Dólar americano', 2),
    ('EUR', 'Euro', 2),
    ('GBP', 'Libra esterlina', 2),
    ('JPY', 'Iene japonês', 0),
    ('CHF', 'Franco suíço', 2),
    ('ARS', 'Peso argentino', 2),
    ('CNY', 'Yuan chinês', 2);
