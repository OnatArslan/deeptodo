-- users için optimistic locking gerekmiyor (spec §21); @Version yalnız todos ve workspace_members'ta olacak.
alter table users
    drop column version;
