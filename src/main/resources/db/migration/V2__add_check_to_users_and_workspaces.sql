alter table users
    add constraint ck_users_email_lowercase check ( users.email = lower(users.email) ),
    add constraint ck_users_display_name_blank check ( btrim(users.display_name) <> '' )
;

alter table workspaces
    add constraint ck_workspaces_name_blank check ( btrim(workspaces.name) <> '' )




