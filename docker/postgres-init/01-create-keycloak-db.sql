-- Keycloak'ın kendi database'i ve kullanıcısı. Uygulamanın (deeptodo) kullanıcısı bu database'e erişemez.
-- Yalnız local geliştirme parolası; production'da farklı (F2 / E4).
CREATE ROLE keycloak LOGIN PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
