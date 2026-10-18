create table refresh_tokens
(
    id         uuid        not null,
    user_id    uuid        not null,
    family_id  uuid        not null,
    token_hash varchar(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    revoked_at timestamptz,

    constraint pk_refresh_tokens primary key (id),
    constraint uq_refresh_tokens_token_hash unique (token_hash),
    constraint fk_refresh_tokens_user foreign key (user_id) references users (id),
    constraint ck_refresh_tokens_expiry check (expires_at > created_at)
);

-- Bilinen sorgular: deactivation'da kullanıcının, reuse / logout'ta ailenin aktif token'larını revoke etmek.
create index idx_refresh_tokens_user_id on refresh_tokens (user_id);
create index idx_refresh_tokens_family_id on refresh_tokens (family_id);
