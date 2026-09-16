create table usuario (
    id          uuid          not null primary key,
    login       varchar(40)   not null,
    nome        varchar(120)  not null,
    senha_hash  varchar(100)  not null,
    papel       varchar(10)   not null,
    ativo       boolean       not null default true,
    criado_em   timestamp     not null default current_timestamp,
    constraint uk_usuario_login unique (login),
    constraint ck_usuario_papel check (papel in ('ADMIN', 'OPERADOR', 'LEITOR'))
);
